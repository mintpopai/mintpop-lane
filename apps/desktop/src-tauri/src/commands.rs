use crate::app_state::{AppState, BootstrapState, PendingLogin};
use crate::auth::{oidc, pkce, storage};
use crate::link::state::LinkState;
use crate::pty::session::{default_shell, spawn_agent_pty};
use std::io::Read;
use tauri::{AppHandle, Emitter, State};

/// 前端唯一能拿到的链路信息：只有状态枚举，没有端口、密码与 Claude 凭据
#[tauri::command]
pub fn link_status(state: State<'_, AppState>) -> LinkState {
    *state.link_state.lock().unwrap()
}

/// 是否已登录（内存中是否持有 access_token）
#[tauri::command]
pub fn auth_status(state: State<'_, AppState>) -> bool {
    state.access_token.lock().unwrap().is_some()
}

/// 引导配置的当前状态（三态）。前端挂载可能晚于引导完成或失败，光靠事件会漏掉，
/// 因此挂载时先查这个，再监听后续事件；查询结果只在事件尚未到达时才采信
/// （见 Login.vue 里 phase 仍为 UNKNOWN 才应用查询结果的注释）。
#[tauri::command]
pub fn client_config_state(state: State<'_, AppState>) -> BootstrapState {
    state.bootstrap_state.lock().unwrap().clone()
}

/// 重新拉取引导配置，供登录页的重试按钮调用
#[tauri::command]
pub async fn reload_client_config(app: AppHandle) -> Result<(), String> {
    crate::reload_client_config(app).await
}

/// 链路不可用的原因（业务码 + 文案），前端据此渲染购买/续费引导
#[tauri::command]
pub fn link_notice(state: State<'_, AppState>) -> Option<crate::app_state::LinkNotice> {
    state.link_notice.lock().unwrap().clone()
}

/// 手动重连：续费后、网络恢复后由用户主动触发
#[tauri::command]
pub async fn reconnect_link(app: AppHandle) -> LinkState {
    crate::establish_link(&app).await
}

/// 发起登录：生成 PKCE，打开系统浏览器到 Logto 授权页。
/// 授权完成后由 deep link 回调继续，见 lib.rs 的 handle_callback。
#[tauri::command]
pub fn start_login(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    // 配置由服务端在启动时下发，取不到说明引导没跑通
    let cfg = state
        .oidc_config
        .lock()
        .unwrap()
        .clone()
        .ok_or_else(|| "无法连接服务端，请稍后重试".to_string())?;

    let pair = pkce::generate();
    let st = pkce::random_state();
    let url = oidc::build_authorize_url(&cfg, &pair, &st);

    *state.pending_login.lock().unwrap() = Some(PendingLogin {
        verifier: pair.verifier,
        state: st,
    });

    tauri_plugin_opener::OpenerExt::opener(&app)
        .open_url(url, None::<&str>)
        .map_err(|e| e.to_string())
}

/// 退出登录：清空内存令牌与钥匙串。链路会在下一次心跳时随之失效。
#[tauri::command]
pub fn logout(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    *state.access_token.lock().unwrap() = None;
    storage::clear_refresh_token().map_err(|e| e.to_string())?;

    let _ = app.emit("auth://changed", serde_json::json!({ "logged_in": false }));
    Ok(())
}

/// 开一个新的终端会话。链路不活跃时返回错误，前端据此提示用户。
#[tauri::command]
pub fn open_session(
    app: AppHandle,
    state: State<'_, AppState>,
    rows: u16,
    cols: u16,
) -> Result<String, String> {
    let link_state = *state.link_state.lock().unwrap();
    let inbound = state
        .inbound
        .lock()
        .unwrap()
        .clone()
        .ok_or_else(|| "链路尚未就绪".to_string())?;
    // 过渡逻辑（Task 3 换成会话向导按订阅选择）：取止期最晚的 CLAUDE 凭据
    let credential = state
        .link
        .lock()
        .unwrap()
        .as_ref()
        .and_then(|l| {
            l.agent_credentials
                .iter()
                .filter(|c| c.agent_type == "CLAUDE")
                .max_by(|a, b| a.ends_at.cmp(&b.ends_at))
                .map(|c| c.credential.clone())
        })
        .ok_or_else(|| "没有可用的 Claude 席位".to_string())?;

    let session = spawn_agent_pty(
        link_state,
        &inbound,
        &credential,
        default_shell(),
        rows,
        cols,
    )
    .map_err(|e| e.to_string())?;

    let id = new_session_id();
    let mut reader = session.reader().map_err(|e| e.to_string())?;

    // 把子进程输出泵到前端
    let emit_id = id.clone();
    std::thread::spawn(move || {
        let mut buf = [0u8; 8192];
        loop {
            match reader.read(&mut buf) {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    let payload = serde_json::json!({
                        "id": emit_id,
                        "data": String::from_utf8_lossy(&buf[..n]).to_string(),
                    });
                    let _ = app.emit("session://output", payload);
                }
            }
        }
    });

    state.sessions.lock().unwrap().insert(id.clone(), session);
    Ok(id)
}

#[tauri::command]
pub fn write_session(state: State<'_, AppState>, id: String, data: String) -> Result<(), String> {
    let sessions = state.sessions.lock().unwrap();
    let session = sessions.get(&id).ok_or_else(|| "会话不存在".to_string())?;
    session.write(data.as_bytes()).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn resize_session(
    state: State<'_, AppState>,
    id: String,
    rows: u16,
    cols: u16,
) -> Result<(), String> {
    let sessions = state.sessions.lock().unwrap();
    let session = sessions.get(&id).ok_or_else(|| "会话不存在".to_string())?;
    session.resize(rows, cols).map_err(|e| e.to_string())
}

/// 生成会话 id，无需密码学强度
fn new_session_id() -> String {
    use rand::Rng;
    let n: u64 = rand::thread_rng().gen();
    format!("s{n:x}")
}
