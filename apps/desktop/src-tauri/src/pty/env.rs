use crate::link::model::InboundCredentials;

/// 构造 Agent 子进程的环境变量。
/// 大小写两种形式都给：不同工具读的变量名不一致，漏掉一种就可能绕过代理。
pub fn build_agent_env(
    inbound: &InboundCredentials,
    claude_credential: &str,
) -> Vec<(String, String)> {
    let proxy = inbound.proxy_url();
    let no_proxy = "localhost,127.0.0.1,::1".to_string();

    vec![
        ("HTTPS_PROXY".to_string(), proxy.clone()),
        ("HTTP_PROXY".to_string(), proxy.clone()),
        ("ALL_PROXY".to_string(), proxy.clone()),
        ("NO_PROXY".to_string(), no_proxy.clone()),
        ("https_proxy".to_string(), proxy.clone()),
        ("http_proxy".to_string(), proxy.clone()),
        ("all_proxy".to_string(), proxy),
        ("no_proxy".to_string(), no_proxy),
        (
            "CLAUDE_CODE_OAUTH_TOKEN".to_string(),
            claude_credential.to_string(),
        ),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::link::model::InboundCredentials;
    use std::collections::HashMap;

    fn sample_inbound() -> InboundCredentials {
        InboundCredentials {
            port: 27890,
            username: "u".to_string(),
            password: "p".to_string(),
        }
    }

    fn env_map() -> HashMap<String, String> {
        build_agent_env(&sample_inbound(), "sk-ant-test")
            .into_iter()
            .collect()
    }

    #[test]
    fn 三个代理变量都指向本地入站监听() {
        let env = env_map();
        let expected = "http://u:p@127.0.0.1:27890";
        assert_eq!(env["HTTPS_PROXY"], expected);
        assert_eq!(env["HTTP_PROXY"], expected);
        assert_eq!(env["ALL_PROXY"], expected);
    }

    #[test]
    fn 回环地址被排除在代理之外() {
        let env = env_map();
        assert_eq!(env["NO_PROXY"], "localhost,127.0.0.1,::1");
    }

    #[test]
    fn claude凭据被注入() {
        let env = env_map();
        assert_eq!(env["CLAUDE_CODE_OAUTH_TOKEN"], "sk-ant-test");
    }

    #[test]
    fn 代理变量的小写形式同样注入以兼容各类工具() {
        let env = env_map();
        assert_eq!(env["https_proxy"], env["HTTPS_PROXY"]);
        assert_eq!(env["http_proxy"], env["HTTP_PROXY"]);
        assert_eq!(env["no_proxy"], env["NO_PROXY"]);
    }
}
