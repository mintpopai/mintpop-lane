use crate::link::kernel::MihomoKernel;
use crate::link::model::{InboundCredentials, LinkConfig};
use crate::link::state::LinkState;
use crate::pty::session::PtySession;
use serde::Serialize;
use std::collections::HashMap;
use std::sync::Mutex;

/// 一次登录发起时暂存的 PKCE 验证串与 state，用于校验回调
pub struct PendingLogin {
    pub verifier: String,
    pub state: String,
}

/// 链路不可用的原因通知：业务码 + 服务端文案，供前端渲染购买/续费引导。
/// 只描述「为什么连不上」，不含任何凭据或节点信息，可以进渲染层。
#[derive(Debug, Clone, Serialize)]
pub struct LinkNotice {
    pub code: i32,
    pub msg: String,
}

/// 全局应用状态。凭据只存在于此，绝不下发到渲染层。
pub struct AppState {
    pub link_state: Mutex<LinkState>,
    pub inbound: Mutex<Option<InboundCredentials>>,
    pub link: Mutex<Option<LinkConfig>>,
    /// 内核用异步锁：吊销时需要在锁内 await reset_to_empty
    pub kernel: tokio::sync::Mutex<Option<MihomoKernel>>,
    pub sessions: Mutex<HashMap<String, PtySession>>,
    /// 当前自签会话 token，只存内存；持久副本在 OS 钥匙串
    pub session_token: Mutex<Option<String>>,
    pub pending_login: Mutex<Option<PendingLogin>>,
    /// 链路不可用的原因通知，establish_link 拉配置失败时写入，成功时清空
    pub link_notice: Mutex<Option<LinkNotice>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            link_state: Mutex::new(LinkState::DISCONNECTED),
            inbound: Mutex::new(None),
            link: Mutex::new(None),
            kernel: tokio::sync::Mutex::new(None),
            sessions: Mutex::new(HashMap::new()),
            session_token: Mutex::new(None),
            pending_login: Mutex::new(None),
            link_notice: Mutex::new(None),
        }
    }
}
