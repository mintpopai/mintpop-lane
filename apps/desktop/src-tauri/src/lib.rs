pub mod app_state;
pub mod commands;
pub mod link;
pub mod pty;

use app_state::AppState;
use link::inbound::allocate_inbound;
use link::kernel::MihomoKernel;
use link::probe::{verify_egress, EgressVerdict, DEFAULT_PROBE_URL};
use link::render::render_kernel_config;
use link::source::load_local_link;
use link::state::{next_state, LinkEvent, LinkState};
use std::path::PathBuf;
use std::time::Duration;
use tauri::Manager;

/// 第一期：链路配置来自本地文件，路径由环境变量指定
fn local_link_path() -> PathBuf {
    std::env::var("MINTPOP_LINK_FILE")
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("/tmp/mintpop-link.json"))
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

/// 建立链路：读配置 → 拉起内核 → 推送配置 → 校验出口。
/// 任何一步失败都落到非 ACTIVE 状态，从而使 spawn 守卫拒绝启动 Agent。
async fn establish_link(state: &AppState) -> LinkState {
    let mut current = set_state(
        state,
        next_state(
            *state.link_state.lock().unwrap(),
            LinkEvent::CONFIG_FETCH_STARTED,
        ),
    );

    let Ok(link) = load_local_link(&local_link_path()) else {
        return set_state(state, next_state(current, LinkEvent::CONFIG_FETCH_FAILED));
    };

    let Ok(inbound) = allocate_inbound() else {
        return set_state(state, next_state(current, LinkEvent::CONFIG_FETCH_FAILED));
    };

    let work_dir = std::env::temp_dir().join("mintpop-kernel");
    if std::fs::create_dir_all(&work_dir).is_err() {
        return set_state(state, next_state(current, LinkEvent::KERNEL_CRASHED));
    }

    let Ok(kernel) = MihomoKernel::spawn(&mihomo_path(), &work_dir) else {
        return set_state(state, next_state(current, LinkEvent::KERNEL_CRASHED));
    };

    if kernel.wait_ready(Duration::from_secs(15)).await.is_err() {
        return set_state(state, next_state(current, LinkEvent::KERNEL_CRASHED));
    }

    let Ok(yaml) = render_kernel_config(&link, &inbound) else {
        return set_state(state, next_state(current, LinkEvent::CONFIG_FETCH_FAILED));
    };

    if kernel.push_config(&yaml).await.is_err() {
        return set_state(state, next_state(current, LinkEvent::CONFIG_FETCH_FAILED));
    }

    // fail-closed 第二道闸：出口 IP 必须是期望的落地 IP，否则只算走通了第一跳
    let event = match verify_egress(&inbound, &link.expected_egress_ips, DEFAULT_PROBE_URL).await {
        Ok(EgressVerdict::MATCHED(_)) => LinkEvent::EGRESS_VERIFIED,
        _ => LinkEvent::EGRESS_MISMATCHED,
    };
    current = next_state(current, event);

    *state.inbound.lock().unwrap() = Some(inbound);
    *state.link.lock().unwrap() = Some(link);
    *state.kernel.lock().unwrap() = Some(kernel);
    set_state(state, current)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(AppState::default())
        .setup(|app| {
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let state = handle.state::<AppState>();
                establish_link(&state).await;
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::link_status,
            commands::open_session,
            commands::write_session,
            commands::resize_session,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}
