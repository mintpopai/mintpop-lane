use crate::link::model::InboundCredentials;
use std::time::Duration;
use thiserror::Error;

/// 默认探测端点，只回显调用方出口 IP。计划二改为公司自有端点。
pub const DEFAULT_PROBE_URL: &str = "https://api.ipify.org";

const PROBE_TIMEOUT: Duration = Duration::from_secs(15);

#[derive(Debug, Error)]
pub enum ProbeError {
    #[error("构建探测客户端失败：{0}")]
    Client(#[source] reqwest::Error),
    #[error("探测请求失败：{0}")]
    Request(#[source] reqwest::Error),
}

#[allow(non_camel_case_types)]
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EgressVerdict {
    /// 出口 IP 在期望集合内，链路两跳均已走通
    MATCHED(String),
    /// 出口 IP 不在期望集合内：链路断开、或只走通了第一跳
    MISMATCHED {
        actual: String,
        expected: Vec<String>,
    },
}

/// 纯判定逻辑，与网络无关，便于测试
fn judge(actual: &str, expected: &[String]) -> EgressVerdict {
    let actual = actual.trim().to_string();
    if expected.iter().any(|ip| ip == &actual) {
        EgressVerdict::MATCHED(actual)
    } else {
        EgressVerdict::MISMATCHED {
            actual,
            expected: expected.to_vec(),
        }
    }
}

/// 经本地入站监听发一次探测请求，校验最终出口 IP 是否为期望的落地 IP。
/// 这是 fail-closed 的第二道闸：不通过则拒绝启动 Agent。
pub async fn verify_egress(
    inbound: &InboundCredentials,
    expected: &[String],
    probe_url: &str,
) -> Result<EgressVerdict, ProbeError> {
    let proxy = reqwest::Proxy::all(inbound.proxy_url()).map_err(ProbeError::Client)?;
    let client = reqwest::Client::builder()
        .proxy(proxy)
        .timeout(PROBE_TIMEOUT)
        .build()
        .map_err(ProbeError::Client)?;

    let body = client
        .get(probe_url)
        .send()
        .await
        .map_err(ProbeError::Request)?
        .text()
        .await
        .map_err(ProbeError::Request)?;

    Ok(judge(&body, expected))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 出口在期望集合内判定为匹配() {
        let verdict = judge("77.47.143.6", &["77.47.143.6".to_string()]);
        assert!(matches!(verdict, EgressVerdict::MATCHED(_)));
    }

    #[test]
    fn 出口不在期望集合内判定为不匹配() {
        // 只通了第一跳时会看到机场节点的 IP，必须判定为不匹配
        let verdict = judge("1.2.3.4", &["77.47.143.6".to_string()]);
        match verdict {
            EgressVerdict::MISMATCHED { actual, expected } => {
                assert_eq!(actual, "1.2.3.4");
                assert_eq!(expected, vec!["77.47.143.6".to_string()]);
            }
            _ => panic!("应判定为不匹配"),
        }
    }

    #[test]
    fn 期望集合为空时一律判定为不匹配() {
        // 宁可拒绝也不放行：没有期望值就无法证明链路正确
        assert!(matches!(
            judge("1.2.3.4", &[]),
            EgressVerdict::MISMATCHED { .. }
        ));
    }

    #[test]
    fn 探测返回值两端的空白会被忽略() {
        let verdict = judge("  77.47.143.6\n", &["77.47.143.6".to_string()]);
        assert!(matches!(verdict, EgressVerdict::MATCHED(_)));
    }
}
