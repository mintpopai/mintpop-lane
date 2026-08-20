use crate::link::model::{InboundCredentials, LinkConfig, FRONT_NAME, LAND_NAME};
use serde_yaml_ng::{Mapping, Value};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum RenderError {
    #[error("序列化内核配置失败：{0}")]
    Serialize(#[from] serde_yaml_ng::Error),
}

/// 生成引导配置：只开本地控制接口，不含任何节点信息。
/// 内核先以这份空壳启动，真实链路随后经 API 热加载，凭据因此不落盘。
pub fn render_bootstrap_config(controller_port: u16, secret: &str) -> String {
    format!(
        "allow-lan: false\n\
         mode: rule\n\
         log-level: warning\n\
         find-process-mode: off\n\
         external-controller: 127.0.0.1:{controller_port}\n\
         secret: {secret}\n\
         rules:\n\
         \x20 - MATCH,REJECT\n"
    )
}

/// 生成完整内核配置：两跳链式代理 + 钉死出口的回环监听 + 拒绝一切的兜底规则
pub fn render_kernel_config(
    link: &LinkConfig,
    inbound: &InboundCredentials,
) -> Result<String, RenderError> {
    // 覆盖下发端可能带来的 name / dialer-proxy，确保链路拓扑由本地说了算
    let mut front = link.front.clone();
    front.insert(Value::from("name"), Value::from(FRONT_NAME));
    front.remove(Value::from("dialer-proxy"));

    let mut land = link.land.clone();
    land.insert(Value::from("name"), Value::from(LAND_NAME));
    land.insert(Value::from("dialer-proxy"), Value::from(FRONT_NAME));

    let mut user = Mapping::new();
    user.insert(Value::from("username"), Value::from(inbound.username.clone()));
    user.insert(Value::from("password"), Value::from(inbound.password.clone()));

    let mut listener = Mapping::new();
    listener.insert(Value::from("name"), Value::from("agent-in"));
    listener.insert(Value::from("type"), Value::from("mixed"));
    listener.insert(Value::from("listen"), Value::from("127.0.0.1"));
    listener.insert(Value::from("port"), Value::from(inbound.port));
    // 出口钉死在链末端，完全绕开 rules——规则可被绕过，钉死的入站出口不可
    listener.insert(Value::from("proxy"), Value::from(LAND_NAME));
    listener.insert(
        Value::from("users"),
        Value::Sequence(vec![Value::Mapping(user)]),
    );

    let mut root = Mapping::new();
    root.insert(Value::from("allow-lan"), Value::from(false));
    root.insert(Value::from("mode"), Value::from("rule"));
    root.insert(Value::from("log-level"), Value::from("warning"));
    root.insert(Value::from("find-process-mode"), Value::from("off"));
    root.insert(
        Value::from("proxies"),
        Value::Sequence(vec![Value::Mapping(front), Value::Mapping(land)]),
    );
    root.insert(
        Value::from("listeners"),
        Value::Sequence(vec![Value::Mapping(listener)]),
    );
    // 安全不变量：兜底必须是拒绝。配置推送失败或监听失效时宁可断网，绝不直连。
    root.insert(
        Value::from("rules"),
        Value::Sequence(vec![Value::from("MATCH,REJECT")]),
    );

    Ok(serde_yaml_ng::to_string(&Value::Mapping(root))?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::link::model::{AgentCredential, InboundCredentials, LinkConfig};
    use serde_yaml_ng::{Mapping, Value};

    /// 构造一份测试用链路配置
    fn sample_link() -> LinkConfig {
        let mut front = Mapping::new();
        front.insert(Value::from("type"), Value::from("trojan"));
        front.insert(Value::from("server"), Value::from("us.example.com"));
        front.insert(Value::from("port"), Value::from(443));

        let mut land = Mapping::new();
        land.insert(Value::from("type"), Value::from("socks5"));
        land.insert(Value::from("server"), Value::from("77.47.143.6"));
        land.insert(Value::from("port"), Value::from(50101));

        LinkConfig {
            front,
            land,
            expected_egress_ips: vec!["77.47.143.6".to_string()],
            agent_credentials: vec![AgentCredential {
                subscription_id: 1,
                name: "Claude 席位 1".to_string(),
                agent_type: "CLAUDE".to_string(),
                credential: "sk-ant-test".to_string(),
                ends_at: "2026-09-18T12:00:00Z".to_string(),
            }],
            ttl_seconds: 1800,
        }
    }

    fn sample_inbound() -> InboundCredentials {
        InboundCredentials {
            port: 27890,
            username: "u".to_string(),
            password: "p".to_string(),
        }
    }

    #[test]
    fn 生成的配置里两跳节点被正确命名且串成链() {
        let yaml = render_kernel_config(&sample_link(), &sample_inbound()).unwrap();
        let parsed: Mapping = serde_yaml_ng::from_str(&yaml).unwrap();
        let proxies = parsed["proxies"].as_sequence().unwrap();

        assert_eq!(proxies[0]["name"].as_str().unwrap(), "FRONT");
        assert_eq!(proxies[1]["name"].as_str().unwrap(), "LAND");
        // 第二跳必须经第一跳拨出，这就是链式代理
        assert_eq!(proxies[1]["dialer-proxy"].as_str().unwrap(), "FRONT");
        // 第一跳自己不能有 dialer-proxy，否则成环
        assert!(proxies[0].get("dialer-proxy").is_none());
    }

    #[test]
    fn 入站监听只绑回环且出口钉死在链末端() {
        let yaml = render_kernel_config(&sample_link(), &sample_inbound()).unwrap();
        let parsed: Mapping = serde_yaml_ng::from_str(&yaml).unwrap();
        let listener = &parsed["listeners"].as_sequence().unwrap()[0];

        assert_eq!(listener["listen"].as_str().unwrap(), "127.0.0.1");
        assert_eq!(listener["type"].as_str().unwrap(), "mixed");
        assert_eq!(listener["port"].as_u64().unwrap(), 27890);
        // 出口钉死，不经 rules
        assert_eq!(listener["proxy"].as_str().unwrap(), "LAND");

        let user = &listener["users"].as_sequence().unwrap()[0];
        assert_eq!(user["username"].as_str().unwrap(), "u");
        assert_eq!(user["password"].as_str().unwrap(), "p");
    }

    #[test]
    fn 兜底规则必须是拒绝而不是直连() {
        let yaml = render_kernel_config(&sample_link(), &sample_inbound()).unwrap();
        let parsed: Mapping = serde_yaml_ng::from_str(&yaml).unwrap();
        let rules = parsed["rules"].as_sequence().unwrap();

        assert_eq!(rules.len(), 1);
        assert_eq!(rules[0].as_str().unwrap(), "MATCH,REJECT");
        // 安全不变量：配置里任何地方都不能出现 DIRECT 兜底
        assert!(!yaml.contains("MATCH,DIRECT"));
    }

    #[test]
    fn 引导配置不含任何节点信息() {
        let yaml = render_bootstrap_config(29090, "s3cret");
        let parsed: Mapping = serde_yaml_ng::from_str(&yaml).unwrap();

        assert_eq!(
            parsed["external-controller"].as_str().unwrap(),
            "127.0.0.1:29090"
        );
        assert_eq!(parsed["secret"].as_str().unwrap(), "s3cret");
        // 空壳：没有节点、没有入站监听
        assert!(parsed.get("proxies").is_none());
        assert!(parsed.get("listeners").is_none());
        assert_eq!(
            parsed["rules"].as_sequence().unwrap()[0].as_str().unwrap(),
            "MATCH,REJECT"
        );
    }

    #[test]
    fn 节点配置里的名字会被覆盖以防下发端注入别名() {
        let mut link = sample_link();
        link.front
            .insert(Value::from("name"), Value::from("恶意别名"));
        let yaml = render_kernel_config(&link, &sample_inbound()).unwrap();
        let parsed: Mapping = serde_yaml_ng::from_str(&yaml).unwrap();

        assert_eq!(
            parsed["proxies"].as_sequence().unwrap()[0]["name"]
                .as_str()
                .unwrap(),
            "FRONT"
        );
        assert!(!yaml.contains("恶意别名"));
    }
}
