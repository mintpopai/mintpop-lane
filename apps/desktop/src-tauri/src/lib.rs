pub mod app_state;
pub mod auth;
pub mod commands;
pub mod link;
pub mod pty;

use app_state::AppState;
use link::inbound::allocate_inbound;
use link::kernel::MihomoKernel;
use link::probe::{verify_egress, EgressVerdict, DEFAULT_PROBE_URL};
use link::render::render_kernel_config;
use link::state::{next_state, LinkEvent, LinkState};
use std::path::PathBuf;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager};

/// 心跳间隔。既是吊销的生效延迟上限，也是链路异常的发现延迟上限。
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(60);

/// 取服务端地址：构建期注入的值优先，缺失时回退到运行时环境变量（开发用）。
/// 它是整条信任链的锚点——客户端凭它决定去哪儿要登录配置，因此只能编译进二进制，
/// 绝不做成用户可改的本地配置。
macro_rules! injected {
    ($key:literal) => {
        option_env!($key)
            .map(str::to_string)
            .or_else(|| std::env::var($key).ok())
    };
}

pub fn server_base_url() -> String {
    injected!("MINTPOP_SERVER_URL").unwrap_or_else(|| "http://127.0.0.1:8080".to_string())
}

/// 定位 mihomo 内核。
/// 打包后它作为 sidecar 与主程序同级；开发时允许用 MIHOMO_BIN 指向任意二进制。
fn mihomo_path() -> PathBuf {
    if let Ok(custom) = std::env::var("MIHOMO_BIN") {
        return PathBuf::from(custom);
    }

    let name = if cfg!(windows) { "mihomo.exe" } else { "mihomo" };
    std::env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(|dir| dir.join(name)))
        .unwrap_or_else(|| PathBuf::from(name))
}

/// 记录状态并返回，避免每处都重复写锁操作
fn set_state(state: &AppState, next: LinkState) -> LinkState {
    *state.link_state.lock().unwrap() = next;
    next
}

fn advance(state: &AppState, event: LinkEvent) -> LinkState {
    let current = *state.link_state.lock().unwrap();
    set_state(state, next_state(current, event))
}

/// 建立链路：拉配置 → 拉起内核 → 推送配置 → 校验出口。
/// 任何一步失败都落到非 ACTIVE 状态，从而使 spawn 守卫拒绝启动 Agent。
pub async fn establish_link(state: &AppState) -> LinkState {
    let Some(token) = state.access_token.lock().unwrap().clone() else {
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    };

    advance(state, LinkEvent::CONFIG_FETCH_STARTED);

    let link = match link::remote::fetch_link(&server_base_url(), &token).await {
        Ok(link) => link,
        Err(e) => {
            log_error("拉取链路配置失败", &e);
            return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
        }
    };

    let Ok(inbound) = allocate_inbound() else {
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    };

    let work_dir = std::env::temp_dir().join("mintpop-kernel");
    if std::fs::create_dir_all(&work_dir).is_err() {
        return advance(state, LinkEvent::KERNEL_CRASHED);
    }

    let kernel = match MihomoKernel::spawn(&mihomo_path(), &work_dir) {
        Ok(k) => k,
        Err(e) => {
            log_error("启动内核失败", &e);
            return advance(state, LinkEvent::KERNEL_CRASHED);
        }
    };

    if kernel.wait_ready(Duration::from_secs(15)).await.is_err() {
        return advance(state, LinkEvent::KERNEL_CRASHED);
    }

    let Ok(yaml) = render_kernel_config(&link, &inbound) else {
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    };

    if let Err(e) = kernel.push_config(&yaml).await {
        log_error("推送内核配置失败", &e);
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    }

    // fail-closed 第二道闸：出口 IP 必须是该用户绑定的落地 IP，
    // 只走通第一跳时看到的是机场节点 IP，同样会被判为不匹配。
    let event = match verify_egress(&inbound, &link.expected_egress_ips, DEFAULT_PROBE_URL).await {
        Ok(EgressVerdict::MATCHED(_)) => LinkEvent::EGRESS_VERIFIED,
        _ => LinkEvent::EGRESS_MISMATCHED,
    };

    *state.inbound.lock().unwrap() = Some(inbound);
    *state.link.lock().unwrap() = Some(link);
    *state.kernel.lock().await = Some(kernel);
    advance(state, event)
}

/// 心跳循环：定期确认用户仍可用。被吊销时网络当场断开，
/// 但不 kill 用户进程，让 Agent 自己报网络错误，避免丢失正在进行的工作。
async fn heartbeat_loop(app: AppHandle) {
    let base_url = server_base_url();
    loop {
        tokio::time::sleep(HEARTBEAT_INTERVAL).await;

        let state = app.state::<AppState>();
        let Some(token) = state.access_token.lock().unwrap().clone() else {
            continue;
        };

        match link::remote::heartbeat(&base_url, &token).await {
            Ok(link::remote::UserStatus::ACTIVE) => {}
            Ok(_) => {
                reset_kernel(&state).await;
                advance(&state, LinkEvent::REVOKED_BY_SERVER);
            }
            Err(e) => {
                // 心跳打不通同样按不可用处理，绝不假定链路仍然有效
                log_error("心跳失败", &e);
                reset_kernel(&state).await;
                advance(&state, LinkEvent::NETWORK_LOST);
            }
        }
    }
}

/// 把内核打回空壳状态，流量当场断开
async fn reset_kernel(state: &AppState) {
    if let Some(kernel) = state.kernel.lock().await.as_ref() {
        let _ = kernel.reset_to_empty().await;
    }
}

/// 登录成功后的收尾：存令牌、建链路、通知前端
async fn on_authenticated(app: &AppHandle, tokens: auth::oidc::TokenSet) {
    let state = app.state::<AppState>();

    *state.access_token.lock().unwrap() = Some(tokens.access_token);
    if let Some(refresh) = tokens.refresh_token {
        // refresh_token 是唯一允许落地的凭据，且只能进钥匙串
        if let Err(e) = auth::storage::save_refresh_token(&refresh) {
            log_error("保存 refresh_token 失败", &e);
        }
    }

    let _ = app.emit("auth://changed", serde_json::json!({ "logged_in": true }));
    establish_link(&state).await;
}

/// 处理 deep link 回调，完成授权码交换
async fn handle_callback(app: AppHandle, url: String) {
    let pending = {
        let state = app.state::<AppState>();
        let mut guard = state.pending_login.lock().unwrap();
        guard.take()
    };

    let Some(pending) = pending else {
        return;
    };

    let Some(cfg) = app.state::<AppState>().oidc_config.lock().unwrap().clone() else {
        log_error("回调到达时尚无登录配置", &"引导未完成");
        return;
    };
    let code = match auth::oidc::extract_code(&url, &pending.state) {
        Ok(code) => code,
        Err(e) => {
            log_error("回调校验失败", &e);
            return;
        }
    };

    match auth::oidc::exchange_code(&cfg, &code, &pending.verifier).await {
        Ok(tokens) => on_authenticated(&app, tokens).await,
        Err(e) => log_error("授权码换令牌失败", &e),
    }
}

/// 启动引导：拉取登录接入配置。成功后才谈得上登录，因此静默登录串在它之后。
///
/// 用 bootstrap_lock 把整个函数体串行化：启动时的自动引导与用户点重试触发的
/// 重新引导可能并发发生，两者都会走到 try_silent_login 用同一个 refresh_token
/// 换新令牌，见 AppState::bootstrap_lock 的注释。持锁跨越下面的 await 是有意为之。
async fn bootstrap(app: AppHandle) -> Result<(), String> {
    let state = app.state::<AppState>();
    let _guard = state.bootstrap_lock.lock().await;

    match auth::bootstrap::fetch_client_config(&server_base_url()).await {
        Ok(cfg) => {
            *state.oidc_config.lock().unwrap() = Some(cfg);
            let _ = app.emit("auth://config-ready", ());
            try_silent_login(app.clone()).await;
            Ok(())
        }
        Err(e) => {
            log_error("拉取登录配置失败", &e);
            let reason = e.to_string();
            let _ = app.emit("auth://config-failed", serde_json::json!({ "reason": reason }));
            Err(reason)
        }
    }
}

/// 供命令层调用的重试入口
pub async fn reload_client_config(app: AppHandle) -> Result<(), String> {
    bootstrap(app).await
}

/// 启动时若钥匙串里已有 refresh_token，尝试静默登录，省掉用户每次授权
async fn try_silent_login(app: AppHandle) {
    let Ok(Some(refresh_token)) = auth::storage::load_refresh_token() else {
        return;
    };

    let Some(cfg) = app.state::<AppState>().oidc_config.lock().unwrap().clone() else {
        return;
    };

    match auth::oidc::refresh(&cfg, &refresh_token).await {
        Ok(tokens) => on_authenticated(&app, tokens).await,
        Err(e) => {
            // 刷新失败通常意味着用户已在 Logto 侧被停用，清掉本地残留
            log_error("静默登录失败", &e);
            let _ = auth::storage::clear_refresh_token();
        }
    }
}

fn log_error(context: &str, e: &dyn std::fmt::Display) {
    eprintln!("[mintpop] {context}：{e}");
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default();

    // single-instance 必须最先注册，否则桌面端收不到 deep link 事件
    #[cfg(desktop)]
    {
        builder = builder.plugin(tauri_plugin_single_instance::init(|_app, _argv, _cwd| {}));
    }

    builder
        .plugin(tauri_plugin_deep_link::init())
        .plugin(tauri_plugin_opener::init())
        .manage(AppState::default())
        .setup(|app| {
            use tauri_plugin_deep_link::DeepLinkExt;

            let handle = app.handle().clone();
            app.deep_link().on_open_url(move |event| {
                for url in event.urls() {
                    let handle = handle.clone();
                    let url = url.to_string();
                    tauri::async_runtime::spawn(handle_callback(handle, url));
                }
            });

            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let _ = bootstrap(handle).await;
            });

            let handle = app.handle().clone();
            tauri::async_runtime::spawn(heartbeat_loop(handle));

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::link_status,
            commands::open_session,
            commands::write_session,
            commands::resize_session,
            commands::auth_status,
            commands::start_login,
            commands::logout,
            commands::client_config_ready,
            commands::reload_client_config,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}
