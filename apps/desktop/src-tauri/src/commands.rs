use crate::app_state::{AppState, PendingLogin};
use crate::auth::{pkce, storage};
use crate::link::state::LinkState;
use crate::pty::session::spawn_agent_pty;
use std::io::Read;
use tauri::{AppHandle, Emitter, Manager, State};

/// 前端唯一能拿到的链路信息：只有状态枚举，没有端口、密码与 Claude 凭据
#[tauri::command]
pub fn link_status(state: State<'_, AppState>) -> LinkState {
    *state.link_state.lock().unwrap()
}

/// 是否已登录（内存中是否持有 session_token）
#[tauri::command]
pub fn auth_status(state: State<'_, AppState>) -> bool {
    state.session_token.lock().unwrap().is_some()
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

/// 发起登录：生成 PKCE，打开系统浏览器到服务端登录入口。
/// 服务端完成与 Logto 的全部握手后经 deep link 送回一次性 ticket，见 lib.rs 的 handle_callback。
#[tauri::command]
pub fn start_login(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let pair = pkce::generate();
    let st = pkce::random_state();
    let url = crate::auth::session::build_start_url(&crate::server_base_url(), &pair.challenge, &st);

    *state.pending_login.lock().unwrap() = Some(PendingLogin {
        verifier: pair.verifier,
        state: st,
    });

    tauri_plugin_opener::OpenerExt::opener(&app)
        .open_url(url, None::<&str>)
        .map_err(|e| e.to_string())
}

/// 退出登录：清空内存令牌与钥匙串，并重置内核回到 DISCONNECTED。
/// 先断链、清内存令牌，钥匙串删除失败只记录不阻断——不能因为钥匙串报错就
/// 留下「内存已清、内核未重置、事件未发」的半登出态（否则下次启动会自动登回去）。
#[tauri::command]
pub async fn logout(app: AppHandle) -> Result<(), String> {
    crate::reset_kernel_and_disconnect(&app).await;
    {
        let state = app.state::<AppState>();
        *state.session_token.lock().unwrap() = None;
    }
    // 与 force_relogin 一致：钥匙串删除失败只记录，不阻断登出
    if let Err(e) = storage::clear_session_token() {
        eprintln!("[lane] 登出时清理钥匙串失败：{e}");
    }
    let _ = app.emit("auth://changed", serde_json::json!({ "logged_in": false }));
    Ok(())
}

/// 前端可见的席位视图：给会话向导渲染用，凭据本体绝不出现
#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AgentCredentialView {
    pub subscription_id: i64,
    pub name: String,
    pub agent_type: String,
    pub display_name: String,
    pub ends_at: String,
}

/// 列出可建会话的席位（只含本客户端认识的 agent 类型）
#[tauri::command]
pub fn list_agent_credentials(
    state: State<'_, AppState>,
) -> Result<Vec<AgentCredentialView>, String> {
    let link = state.link.lock().unwrap();
    let link = link.as_ref().ok_or_else(|| "链路尚未就绪".to_string())?;

    Ok(link
        .agent_credentials
        .iter()
        .filter_map(|c| {
            crate::pty::agent::spec_of(&c.agent_type).map(|spec| AgentCredentialView {
                subscription_id: c.subscription_id,
                name: c.name.clone(),
                agent_type: c.agent_type.clone(),
                display_name: spec.display_name.to_string(),
                ends_at: c.ends_at.clone(),
            })
        })
        .collect())
}

/// 弹系统目录选择器让用户挑 workspace。取消返回 None。
#[tauri::command]
pub async fn pick_workspace(app: AppHandle) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::DialogExt;
    let (tx, rx) = tokio::sync::oneshot::channel();
    app.dialog().file().pick_folder(move |folder| {
        let _ = tx.send(folder);
    });
    let folder = rx.await.map_err(|e| e.to_string())?;
    Ok(folder.map(|p| p.to_string()))
}

/// 开一个新的 agent 会话：按所选订阅注入对应凭据，在所选 workspace 里直接运行 agent CLI
#[tauri::command]
pub fn open_session(
    app: AppHandle,
    state: State<'_, AppState>,
    rows: u16,
    cols: u16,
    subscription_id: i64,
    workspace: String,
) -> Result<String, String> {
    let link_state = *state.link_state.lock().unwrap();
    let inbound = state
        .inbound
        .lock()
        .unwrap()
        .clone()
        .ok_or_else(|| "链路尚未就绪".to_string())?;

    // 按订阅定位凭据与 agent 映射；凭据只在这个作用域内出现，不进前端
    let (command, credential_env, credential) = {
        let link = state.link.lock().unwrap();
        let link = link.as_ref().ok_or_else(|| "链路尚未就绪".to_string())?;
        let chosen = link
            .agent_credentials
            .iter()
            .find(|c| c.subscription_id == subscription_id)
            .ok_or_else(|| "所选套餐不存在或已失效，请刷新后重选".to_string())?;
        let spec = crate::pty::agent::spec_of(&chosen.agent_type)
            .ok_or_else(|| "本版本暂不支持该 agent，请升级客户端".to_string())?;
        (spec.command, spec.credential_env, chosen.credential.clone())
    };

    let session = spawn_agent_pty(
        link_state,
        &inbound,
        command,
        credential_env,
        &credential,
        std::path::Path::new(&workspace),
        rows,
        cols,
    )
    .map_err(|e| e.to_string())?;

    let id = new_session_id();
    let mut reader = session.reader().map_err(|e| e.to_string())?;

    // 把子进程输出泵到前端；子进程退出时通知前端回到向导
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
        let _ = app.emit("session://exit", serde_json::json!({ "id": emit_id }));
    });

    state.sessions.lock().unwrap().insert(id.clone(), session);
    Ok(id)
}

/// 关闭会话：显式杀子进程并移出会话表
#[tauri::command]
pub fn close_session(state: State<'_, AppState>, id: String) {
    if let Some(session) = state.sessions.lock().unwrap().remove(&id) {
        session.kill();
    }
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
