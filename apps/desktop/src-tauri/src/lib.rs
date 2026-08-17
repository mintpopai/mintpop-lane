pub mod app_state;
pub mod auth;
pub mod commands;
pub mod link;
pub mod pty;

use app_state::AppState;
use auth::oidc::OidcConfig;
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

/// OIDC 配置。当前从环境变量读，计划三改为打包期注入的编译期常量。
pub fn oidc_config() -> OidcConfig {
    OidcConfig {
        issuer: std::env::var("MINTPOP_LOGTO_ISSUER").unwrap_or_default(),
        client_id: std::env::var("MINTPOP_LOGTO_CLIENT_ID").unwrap_or_default(),
        redirect_uri: "mintpop://callback".to_string(),
        resource: std::env::var("MINTPOP_API_RESOURCE").unwrap_or_default(),
    }
}

pub fn server_base_url() -> String {
    std::env::var("MINTPOP_SERVER_URL").unwrap_or_else(|_| "http://127.0.0.1:8080".to_string())
}

fn mihomo_path() -> PathBuf {
    std::env::var("MIHOMO_BIN")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("mihomo"))
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

    // fail-closed 第二道闸：出口 IP 必须是该员工绑定的落地 IP，
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

/// 心跳循环：定期确认员工仍可用。被吊销时网络当场断开，
/// 但不 kill 员工进程，让 Agent 自己报网络错误，避免丢失正在进行的工作。
async fn heartbeat_loop(app: AppHandle) {
    let base_url = server_base_url();
    loop {
        tokio::time::sleep(HEARTBEAT_INTERVAL).await;

        let state = app.state::<AppState>();
        let Some(token) = state.access_token.lock().unwrap().clone() else {
            continue;
        };

        match link::remote::heartbeat(&base_url, &token).await {
            Ok(link::remote::EmployeeStatus::ACTIVE) => {}
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

    let cfg = oidc_config();
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

/// 启动时若钥匙串里已有 refresh_token，尝试静默登录，省掉员工每次授权
async fn try_silent_login(app: AppHandle) {
    let Ok(Some(refresh_token)) = auth::storage::load_refresh_token() else {
        return;
    };

    match auth::oidc::refresh(&oidc_config(), &refresh_token).await {
        Ok(tokens) => on_authenticated(&app, tokens).await,
        Err(e) => {
            // 刷新失败通常意味着员工已在 Logto 侧被停用，清掉本地残留
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
            tauri::async_runtime::spawn(try_silent_login(handle));

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
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}
