use crate::auth::oidc::OidcConfig;
use crate::link::kernel::MihomoKernel;
use crate::link::model::{InboundCredentials, LinkConfig};
use crate::link::state::LinkState;
use crate::pty::session::PtySession;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Mutex;

/// 一次登录发起时暂存的 PKCE 验证串与 state，用于校验回调
pub struct PendingLogin {
    pub verifier: String,
    pub state: String,
}

/// 引导（bootstrap）结果的三态。仅靠 `oidc_config: Option<T>` 只能表达"成功与否"，
/// 无法区分"还在飞行中"与"已经失败"——而前端挂载时的补查（client_config_state）
/// 恰恰需要分清这两者：引导可能在组件挂载前就已经失败（服务端不可达时常见，
/// ECONNREFUSED 通常比 webview 加载 bundle 更快），此时 auth://config-failed
/// 事件早已发出且不会被重放，补查若答不出"已失败"就会让登录页永久卡在
/// UNKNOWN（无重试按钮）的中性态。
///
/// 序列化为 adjacently-tagged 的扁平对象，方便前端按 `phase` 分支、
/// `FAILED` 态下 `reason` 与 phase 同级（而非嵌套一层）：
///   UNKNOWN -> {"phase":"UNKNOWN"}
///   READY   -> {"phase":"READY"}
///   FAILED  -> {"phase":"FAILED","reason":"..."}
#[allow(non_camel_case_types)]
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "phase", content = "reason")]
pub enum BootstrapState {
    /// 还没听到引导结果：可能仍在请求中，也可能事件早于监听者注册
    UNKNOWN,
    /// 引导成功，oidc_config 已可用
    READY,
    /// 引导失败，携带与 auth://config-failed 事件同一个 reason 字符串
    FAILED(String),
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
    /// 服务端下发的登录接入配置，启动时拉取；拉不到则无法登录
    pub oidc_config: Mutex<Option<OidcConfig>>,
    /// 引导结果的三态，供前端挂载时补查（见 BootstrapState 注释）
    pub bootstrap_state: Mutex<BootstrapState>,
    /// 引导（bootstrap）互斥锁：串行化自动引导与用户点重试触发的重新引导，
    /// 防止两次并发的静默登录同时用同一个 refresh_token 换新令牌——
    /// Logto 每次刷新都会轮换 refresh_token，后完成的一次会读到已作废的旧值，
    /// 失败分支若清空钥匙串会连带抹掉先完成那次刚保存的新令牌。
    pub bootstrap_lock: tokio::sync::Mutex<()>,
    /// 当前 access_token，只存内存；refresh_token 另存 OS 钥匙串
    pub access_token: Mutex<Option<String>>,
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
            oidc_config: Mutex::new(None),
            bootstrap_state: Mutex::new(BootstrapState::UNKNOWN),
            bootstrap_lock: tokio::sync::Mutex::new(()),
            access_token: Mutex::new(None),
            pending_login: Mutex::new(None),
            link_notice: Mutex::new(None),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // 这是前端消费的契约，值得钉住：三态必须序列化为扁平对象，
    // FAILED 的 reason 与 phase 同级，前端才能用同一套 `payload.phase` /
    // `payload.reason` 读法处理三种情况，不必按 variant 分别解嵌套结构。
    #[test]
    fn 三态序列化为扁平对象供前端按phase字段分支() {
        assert_eq!(
            serde_json::to_value(&BootstrapState::UNKNOWN).unwrap(),
            serde_json::json!({ "phase": "UNKNOWN" })
        );
        assert_eq!(
            serde_json::to_value(&BootstrapState::READY).unwrap(),
            serde_json::json!({ "phase": "READY" })
        );
        assert_eq!(
            serde_json::to_value(BootstrapState::FAILED("服务端不可达".to_string())).unwrap(),
            serde_json::json!({ "phase": "FAILED", "reason": "服务端不可达" })
        );
    }
}
