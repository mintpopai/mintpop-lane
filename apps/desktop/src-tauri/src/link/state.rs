use serde::{Deserialize, Serialize};

/// 链路状态。成员名与序列化取值逐字一致，前端与服务端镜像同一套名字。
#[allow(non_camel_case_types)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum LinkState {
    /// 未连接：未登录、配置拉取失败、内核崩溃或断网
    DISCONNECTED,
    /// 连接中：正在拉取配置并推送给内核
    CONNECTING,
    /// 活跃：链路两跳走通且出口 IP 校验通过，唯一允许启动 Agent 的状态
    ACTIVE,
    /// 降级：出口 IP 校验不通过，禁止启动新 Agent
    DEGRADED,
    /// 已吊销：服务端判定该员工不可用，终态
    REVOKED,
}

#[allow(non_camel_case_types)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LinkEvent {
    CONFIG_FETCH_STARTED,
    CONFIG_FETCH_FAILED,
    EGRESS_VERIFIED,
    EGRESS_MISMATCHED,
    REVOKED_BY_SERVER,
    KERNEL_CRASHED,
    NETWORK_LOST,
}

impl LinkState {
    /// 是否允许启动 Agent 子进程。安全不变量：只有 ACTIVE 放行。
    pub fn allows_spawn(&self) -> bool {
        matches!(self, LinkState::ACTIVE)
    }
}

/// 状态转移表。吊销是终态，其余事件按表转移。
pub fn next_state(current: LinkState, event: LinkEvent) -> LinkState {
    // 吊销是终态，任何事件都不能把它改回可用
    if current == LinkState::REVOKED {
        return LinkState::REVOKED;
    }

    match event {
        LinkEvent::REVOKED_BY_SERVER => LinkState::REVOKED,
        LinkEvent::CONFIG_FETCH_STARTED => LinkState::CONNECTING,
        LinkEvent::CONFIG_FETCH_FAILED => LinkState::DISCONNECTED,
        LinkEvent::EGRESS_VERIFIED => LinkState::ACTIVE,
        LinkEvent::EGRESS_MISMATCHED => LinkState::DEGRADED,
        LinkEvent::KERNEL_CRASHED | LinkEvent::NETWORK_LOST => LinkState::DISCONNECTED,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 只有活跃状态允许启动子进程() {
        assert!(LinkState::ACTIVE.allows_spawn());
        // 其余状态一律不允许——这是安全不变量的落点
        assert!(!LinkState::DISCONNECTED.allows_spawn());
        assert!(!LinkState::CONNECTING.allows_spawn());
        assert!(!LinkState::DEGRADED.allows_spawn());
        assert!(!LinkState::REVOKED.allows_spawn());
    }

    #[test]
    fn 拉取配置成功并校验出口后进入活跃() {
        let s = next_state(LinkState::DISCONNECTED, LinkEvent::CONFIG_FETCH_STARTED);
        assert_eq!(s, LinkState::CONNECTING);
        let s = next_state(s, LinkEvent::EGRESS_VERIFIED);
        assert_eq!(s, LinkState::ACTIVE);
    }

    #[test]
    fn 拉取配置失败回到断开() {
        let s = next_state(LinkState::CONNECTING, LinkEvent::CONFIG_FETCH_FAILED);
        assert_eq!(s, LinkState::DISCONNECTED);
    }

    #[test]
    fn 出口校验不匹配进入降级() {
        let s = next_state(LinkState::ACTIVE, LinkEvent::EGRESS_MISMATCHED);
        assert_eq!(s, LinkState::DEGRADED);
    }

    #[test]
    fn 降级后重新校验通过可回到活跃() {
        let s = next_state(LinkState::DEGRADED, LinkEvent::EGRESS_VERIFIED);
        assert_eq!(s, LinkState::ACTIVE);
    }

    #[test]
    fn 服务端吊销从任何状态都进入已吊销() {
        for from in [
            LinkState::DISCONNECTED,
            LinkState::CONNECTING,
            LinkState::ACTIVE,
            LinkState::DEGRADED,
        ] {
            assert_eq!(
                next_state(from, LinkEvent::REVOKED_BY_SERVER),
                LinkState::REVOKED
            );
        }
    }

    #[test]
    fn 已吊销状态不会被普通事件改回可用() {
        // 吊销是终态，只能靠重新登录（即重建状态机）脱离
        for ev in [
            LinkEvent::EGRESS_VERIFIED,
            LinkEvent::CONFIG_FETCH_STARTED,
            LinkEvent::KERNEL_CRASHED,
        ] {
            assert_eq!(next_state(LinkState::REVOKED, ev), LinkState::REVOKED);
        }
    }

    #[test]
    fn 内核崩溃与断网都回到断开() {
        assert_eq!(
            next_state(LinkState::ACTIVE, LinkEvent::KERNEL_CRASHED),
            LinkState::DISCONNECTED
        );
        assert_eq!(
            next_state(LinkState::ACTIVE, LinkEvent::NETWORK_LOST),
            LinkState::DISCONNECTED
        );
    }

    #[test]
    fn 状态序列化为大写下划线字符串() {
        let json = serde_json::to_string(&LinkState::ACTIVE).unwrap();
        assert_eq!(json, "\"ACTIVE\"");
    }
}
