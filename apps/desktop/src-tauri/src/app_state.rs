use crate::auth::oidc::OidcConfig;
use crate::link::kernel::MihomoKernel;
use crate::link::model::{InboundCredentials, LinkConfig};
use crate::link::state::LinkState;
use crate::pty::session::PtySession;
use std::collections::HashMap;
use std::sync::Mutex;

/// 一次登录发起时暂存的 PKCE 验证串与 state，用于校验回调
pub struct PendingLogin {
    pub verifier: String,
    pub state: String,
}

/// 全局应用状态。凭据只存在于此，绝不下发到渲染层。
pub struct AppState {
    pub link_state: Mutex<LinkState>,
    pub inbound: Mutex<Option<InboundCredentials>>,
    pub link: Mutex<Option<LinkConfig>>,
    /// 内核用异步锁：吊销时需要在锁内 await reset_to_empty
    pub kernel: tokio::sync::Mutex<Option<MihomoKernel>>,
    pub sessions: Mutex<HashMap<String, PtySession>>,
    /// 服务端下发的登录接入配置，启动时拉取；拉不到则无法登录
    pub oidc_config: Mutex<Option<OidcConfig>>,
    /// 引导（bootstrap）互斥锁：串行化自动引导与用户点重试触发的重新引导，
    /// 防止两次并发的静默登录同时用同一个 refresh_token 换新令牌——
    /// Logto 每次刷新都会轮换 refresh_token，后完成的一次会读到已作废的旧值，
    /// 失败分支若清空钥匙串会连带抹掉先完成那次刚保存的新令牌。
    pub bootstrap_lock: tokio::sync::Mutex<()>,
    /// 当前 access_token，只存内存；refresh_token 另存 OS 钥匙串
    pub access_token: Mutex<Option<String>>,
    pub pending_login: Mutex<Option<PendingLogin>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            link_state: Mutex::new(LinkState::DISCONNECTED),
            inbound: Mutex::new(None),
            link: Mutex::new(None),
            kernel: tokio::sync::Mutex::new(None),
            sessions: Mutex::new(HashMap::new()),
            oidc_config: Mutex::new(None),
            bootstrap_lock: tokio::sync::Mutex::new(()),
            access_token: Mutex::new(None),
            pending_login: Mutex::new(None),
        }
    }
}
