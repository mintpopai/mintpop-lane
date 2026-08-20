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
    injected!("LANE_SERVER_URL").unwrap_or_else(|| "http://127.0.0.1:8080".to_string())
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
pub async fn establish_link(app: &AppHandle) -> LinkState {
    let state = app.state::<AppState>();
    let state = &*state;

    // 入口先清通知：上一次失败留下的引导文案不该残留到这一轮
    *state.link_notice.lock().unwrap() = None;

    let Some(token) = state.session_token.lock().unwrap().clone() else {
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    };

    advance(state, LinkEvent::CONFIG_FETCH_STARTED);

    let link = match link::remote::fetch_link(&server_base_url(), &token).await {
        Ok(link) => link,
        Err(link::remote::RemoteError::Status(code)) if code.as_u16() == 401 => {
            // 会话失效：清登录态回登录页，绝不带着一个已死的 token 空转
            force_relogin(app, "登录已过期，请重新登录").await;
            return LinkState::DISCONNECTED;
        }
        Err(e) => {
            // 业务失败（未购买/到期/资源未分配等）记录给前端渲染引导文案
            if let link::remote::RemoteError::Biz { code, msg } = &e {
                *state.link_notice.lock().unwrap() = Some(app_state::LinkNotice {
                    code: *code,
                    msg: msg.clone(),
                });
            }
            log_error("拉取链路配置失败", &e);
            return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
        }
    };

    let Ok(inbound) = allocate_inbound() else {
        return advance(state, LinkEvent::CONFIG_FETCH_FAILED);
    };

    let work_dir = std::env::temp_dir().join("lane-kernel");
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

/// 心跳结果应采取的动作。抽成纯函数是为了让安全语义可测：
/// SUSPENDED/REVOKED/401 必须清登录态回登录页，EXPIRED 只断链保留登录态。
#[allow(non_camel_case_types, clippy::upper_case_acronyms)]
#[derive(Debug, PartialEq, Eq)]
enum HeartbeatAction {
    /// 一切正常，无操作
    NONE,
    /// 服务到期：断链但保留登录态
    EXPIRE,
    /// 清登录态回登录页，携带给用户看的原因
    FORCE_RELOGIN(&'static str),
    /// 网络类失败：断链，等下一轮心跳
    NETWORK_LOST,
}

fn heartbeat_action(
    result: &Result<link::remote::LinkStatus, link::remote::RemoteError>,
) -> HeartbeatAction {
    use link::remote::{LinkStatus, RemoteError};
    match result {
        Ok(LinkStatus::ACTIVE) => HeartbeatAction::NONE,
        Ok(LinkStatus::EXPIRED) => HeartbeatAction::EXPIRE,
        Ok(LinkStatus::SUSPENDED) => HeartbeatAction::FORCE_RELOGIN("账号已被停用，请联系管理员"),
        Ok(LinkStatus::REVOKED) => HeartbeatAction::FORCE_RELOGIN("账号已被吊销"),
        Err(RemoteError::Status(code)) if code.as_u16() == 401 => {
            HeartbeatAction::FORCE_RELOGIN("登录已过期，请重新登录")
        }
        Err(_) => HeartbeatAction::NETWORK_LOST,
    }
}

/// 心跳循环：定期确认用户仍可用。被吊销时网络当场断开，
/// 但不 kill 用户进程，让 Agent 自己报网络错误，避免丢失正在进行的工作。
async fn heartbeat_loop(app: AppHandle) {
    let base_url = server_base_url();
    loop {
        tokio::time::sleep(HEARTBEAT_INTERVAL).await;

        let state = app.state::<AppState>();
        let Some(token) = state.session_token.lock().unwrap().clone() else {
            continue;
        };

        let result = link::remote::heartbeat(&base_url, &token).await;
        match heartbeat_action(&result) {
            HeartbeatAction::NONE => {}
            HeartbeatAction::EXPIRE => {
                // 服务到期：断链但保留登录态，前端提示续费；续费后点重连即可
                reset_kernel(&state).await;
                advance(&state, LinkEvent::SERVICE_EXPIRED);
            }
            HeartbeatAction::FORCE_RELOGIN(reason) => {
                force_relogin(&app, reason).await;
            }
            HeartbeatAction::NETWORK_LOST => {
                // 心跳打不通同样按不可用处理，绝不假定链路仍然有效
                if let Err(e) = &result {
                    log_error("心跳失败", e);
                }
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

/// 重置内核并置链路为 DISCONNECTED：force_relogin 与 logout 的共用尾段。
pub(crate) async fn reset_kernel_and_disconnect(app: &AppHandle) {
    let state = app.state::<AppState>();
    reset_kernel(&state).await;
    set_state(&state, LinkState::DISCONNECTED);
}

/// 断链并回登录页：会话失效（401）、账号被停用/吊销时的统一出口。
/// 与 EXPIRED 的区别：这里连登录态一起清掉，用户必须重新走浏览器登录。
async fn force_relogin(app: &AppHandle, reason: &str) {
    reset_kernel_and_disconnect(app).await;
    let state = app.state::<AppState>();
    *state.session_token.lock().unwrap() = None;
    let _ = auth::storage::clear_session_token();
    let _ = app.emit(
        "auth://changed",
        serde_json::json!({ "logged_in": false, "reason": reason }),
    );
}

/// 登录成功后的收尾：存令牌、建链路、通知前端
async fn on_authenticated(app: &AppHandle, token: String) {
    let state = app.state::<AppState>();
    *state.session_token.lock().unwrap() = Some(token);

    let _ = app.emit("auth://changed", serde_json::json!({ "logged_in": true }));
    establish_link(app).await;
}

/// 处理 deep link 回调：校验 state、用一次性 ticket + verifier 兑换会话
async fn handle_callback(app: AppHandle, url: String) {
    let pending = {
        let state = app.state::<AppState>();
        let mut guard = state.pending_login.lock().unwrap();
        guard.take()
    };
    let Some(pending) = pending else {
        return;
    };

    use auth::session::CallbackOutcome;
    match auth::session::extract_ticket(&url, &pending.state) {
        Ok(CallbackOutcome::TICKET(ticket)) => {
            match auth::session::exchange_ticket(&server_base_url(), &ticket, &pending.verifier).await {
                Ok(token) => {
                    // 自签会话 token 是唯一允许落地的凭据，且只能进钥匙串
                    if let Err(e) = auth::storage::save_session_token(&token) {
                        log_error("保存会话 token 失败", &e);
                    }
                    on_authenticated(&app, token).await;
                }
                Err(e) => {
                    log_error("兑换会话失败", &e);
                    let _ = app.emit(
                        "auth://login-failed",
                        serde_json::json!({ "reason": "登录票据无效或已过期，请重新登录" }),
                    );
                }
            }
        }
        Ok(CallbackOutcome::LOGIN_FAILED) => {
            let _ = app.emit(
                "auth://login-failed",
                serde_json::json!({ "reason": "登录未完成，请重试" }),
            );
        }
        Err(e) => log_error("回调校验失败", &e),
    }
}

/// 启动验活：钥匙串里有会话 token 就拿去调 /api/me 确认还活着。
/// 401 说明会话已失效（过期/被删），清掉残留留在登录页；
/// 网络不通时不武断丢弃 token——保留登录态，链路自然是 DISCONNECTED，用户可稍后重连。
async fn startup_auth(app: AppHandle) {
    let Ok(Some(token)) = auth::storage::load_session_token() else {
        return;
    };

    match auth::session::fetch_me(&server_base_url(), &token).await {
        Ok(_) => on_authenticated(&app, token).await,
        Err(link::remote::RemoteError::Status(code)) if code.as_u16() == 401 => {
            log_error("启动验活失败", &"会话已失效");
            let _ = auth::storage::clear_session_token();
        }
        Err(e) => {
            // 服务端暂时不可达 ≠ 会话失效：保留 token，进主界面但链路断开
            log_error("启动验活网络失败", &e);
            on_authenticated(&app, token).await;
        }
    }
}

fn log_error(context: &str, e: &dyn std::fmt::Display) {
    eprintln!("[lane] {context}：{e}");
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
        .plugin(tauri_plugin_dialog::init())
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
            tauri::async_runtime::spawn(startup_auth(handle));

            let handle = app.handle().clone();
            tauri::async_runtime::spawn(heartbeat_loop(handle));

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::link_status,
            commands::list_agent_credentials,
            commands::pick_workspace,
            commands::open_session,
            commands::close_session,
            commands::write_session,
            commands::resize_session,
            commands::auth_status,
            commands::start_login,
            commands::logout,
            commands::link_notice,
            commands::reconnect_link,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}

#[cfg(test)]
mod tests {
    use super::*;
    use link::remote::{LinkStatus, RemoteError};

    #[test]
    fn 活跃心跳无操作() {
        assert_eq!(
            heartbeat_action(&Ok(LinkStatus::ACTIVE)),
            HeartbeatAction::NONE
        );
    }

    #[test]
    fn 服务到期只断链保留登录态() {
        assert_eq!(
            heartbeat_action(&Ok(LinkStatus::EXPIRED)),
            HeartbeatAction::EXPIRE
        );
    }

    #[test]
    fn 账号停用强制回登录页且文案含停用() {
        match heartbeat_action(&Ok(LinkStatus::SUSPENDED)) {
            HeartbeatAction::FORCE_RELOGIN(reason) => assert!(reason.contains("停用")),
            other => panic!("应为 FORCE_RELOGIN，实际是 {other:?}"),
        }
    }

    #[test]
    fn 账号吊销强制回登录页() {
        assert_eq!(
            heartbeat_action(&Ok(LinkStatus::REVOKED)),
            HeartbeatAction::FORCE_RELOGIN("账号已被吊销")
        );
    }

    #[test]
    fn 会话过期返回401时强制回登录页() {
        let err = RemoteError::Status(reqwest::StatusCode::UNAUTHORIZED);
        assert_eq!(
            heartbeat_action(&Err(err)),
            HeartbeatAction::FORCE_RELOGIN("登录已过期，请重新登录")
        );
    }

    #[test]
    fn 非401的错误一律按网络失败处理() {
        // 非 401 的 HTTP 状态错误
        let status_err = RemoteError::Status(reqwest::StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(
            heartbeat_action(&Err(status_err)),
            HeartbeatAction::NETWORK_LOST
        );

        // 业务错误（非会话失效类）同样不应被当成需要强制登出的信号
        let biz_err = RemoteError::Biz {
            code: 310003,
            msg: "该用户的链路已被吊销".to_string(),
        };
        assert_eq!(
            heartbeat_action(&Err(biz_err)),
            HeartbeatAction::NETWORK_LOST
        );
    }
}
