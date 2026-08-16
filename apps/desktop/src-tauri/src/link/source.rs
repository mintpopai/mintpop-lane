use crate::link::model::LinkConfig;
use std::path::Path;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SourceError {
    #[error("读取链路配置文件失败：{0}")]
    Read(#[source] std::io::Error),
    #[error("解析链路配置失败：{0}")]
    Parse(#[from] serde_json::Error),
    #[error("链路配置缺少期望出口 IP，无法校验链路")]
    MissingEgressIps,
}

/// 从本地 JSON 文件加载链路配置。
/// 第一期的临时来源，计划二改为向服务端拉取，返回类型保持不变。
pub fn load_local_link(path: &Path) -> Result<LinkConfig, SourceError> {
    let raw = std::fs::read_to_string(path).map_err(SourceError::Read)?;
    let link: LinkConfig = serde_json::from_str(&raw)?;

    if link.expected_egress_ips.is_empty() {
        return Err(SourceError::MissingEgressIps);
    }
    Ok(link)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn 能从json文件读出链路配置() {
        let mut f = tempfile::NamedTempFile::new().unwrap();
        write!(
            f,
            r#"{{
              "front": {{"type": "trojan", "server": "us.example.com", "port": 443}},
              "land": {{"type": "socks5", "server": "77.47.143.6", "port": 50101}},
              "expected_egress_ips": ["77.47.143.6"],
              "claude_credential": "sk-ant-test",
              "ttl_seconds": 1800
            }}"#
        )
        .unwrap();

        let link = load_local_link(f.path()).unwrap();
        assert_eq!(link.expected_egress_ips, vec!["77.47.143.6".to_string()]);
        assert_eq!(link.claude_credential, "sk-ant-test");
        assert_eq!(link.front["server"].as_str().unwrap(), "us.example.com");
    }

    #[test]
    fn 缺少期望出口ip时拒绝加载() {
        let mut f = tempfile::NamedTempFile::new().unwrap();
        write!(
            f,
            r#"{{
              "front": {{"type": "trojan"}},
              "land": {{"type": "socks5"}},
              "expected_egress_ips": [],
              "claude_credential": "sk-ant-test",
              "ttl_seconds": 1800
            }}"#
        )
        .unwrap();

        // 没有期望出口就无法校验链路，等于放弃 fail-closed，必须拒绝
        assert!(matches!(
            load_local_link(f.path()),
            Err(SourceError::MissingEgressIps)
        ));
    }
}
