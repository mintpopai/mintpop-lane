use crate::link::kernel::MihomoKernel;
use crate::link::model::{InboundCredentials, LinkConfig};
use crate::link::state::LinkState;
use crate::pty::session::PtySession;
use std::collections::HashMap;
use std::sync::Mutex;

/// 全局应用状态。凭据只存在于此，绝不下发到渲染层。
pub struct AppState {
    pub link_state: Mutex<LinkState>,
    pub inbound: Mutex<Option<InboundCredentials>>,
    pub link: Mutex<Option<LinkConfig>>,
    pub kernel: Mutex<Option<MihomoKernel>>,
    pub sessions: Mutex<HashMap<String, PtySession>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            link_state: Mutex::new(LinkState::DISCONNECTED),
            inbound: Mutex::new(None),
            link: Mutex::new(None),
            kernel: Mutex::new(None),
            sessions: Mutex::new(HashMap::new()),
        }
    }
}
