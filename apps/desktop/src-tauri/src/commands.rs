use crate::app_state::AppState;
use crate::link::state::LinkState;
use crate::pty::session::{default_shell, spawn_agent_pty};
use std::io::Read;
use tauri::{AppHandle, Emitter, State};

/// 前端唯一能拿到的链路信息：只有状态枚举，没有端口与凭据
#[tauri::command]
pub fn link_status(state: State<'_, AppState>) -> LinkState {
    *state.link_state.lock().unwrap()
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
    let credential = state
        .link
        .lock()
        .unwrap()
        .as_ref()
        .map(|l| l.claude_credential.clone())
        .ok_or_else(|| "链路尚未就绪".to_string())?;

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
