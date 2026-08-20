use crate::link::model::LinkConfig;
use serde::Deserialize;
use serde_yaml_ng::Mapping;
use std::time::Duration;
use thiserror::Error;

const REQUEST_TIMEOUT: Duration = Duration::from_secs(15);

#[derive(Debug, Error)]
pub enum RemoteError {
    #[error("请求服务端失败：{0}")]
    Request(#[source] reqwest::Error),
    #[error("服务端返回错误状态：{0}")]
    Status(reqwest::StatusCode),
    #[error("业务错误 {code}：{msg}")]
    Biz { code: i32, msg: String },
    #[error("服务端配置异常，请联系管理员：{0}")]
    InvalidConfig(String),
}

/// 镜像服务端的统一返回体。HTTP 一律 200，成败只看 code。
#[derive(Debug, Deserialize)]
pub struct ApiResponse<T> {
    pub code: i32,
    pub data: Option<T>,
    pub msg: Option<String>,
}

impl<T> ApiResponse<T> {
    pub fn into_data(self) -> Result<T, RemoteError> {
        if self.code == 0 {
            self.data.ok_or(RemoteError::Biz {
                code: self.code,
                msg: "服务端返回空数据".to_string(),
            })
        } else {
            Err(RemoteError::Biz {
                code: self.code,
                msg: self.msg.unwrap_or_else(|| "未知业务错误".to_string()),
            })
        }
    }
}

/// 心跳返回的链路状态，与服务端 LinkStatus 逐字对应。
/// EXPIRED = 账号正常但在期订阅归零：断链但保留登录态，提示续费。
#[allow(non_camel_case_types)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
pub enum LinkStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED,
    EXPIRED,
}

#[derive(Debug, Deserialize)]
pub struct HeartbeatData {
    pub status: LinkStatus,
}

/// 单条可用席位。agent_type 用 String 而非枚举：
/// 服务端新增 agent 类型时，旧客户端要能整体解析成功并忽略未知项。
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AgentCredentialData {
    pub subscription_id: i64,
    pub name: String,
    pub agent_type: String,
    pub credential: String,
    /// ISO-8601 字符串，仅用于展示与排序，不解析为时间类型
    pub ends_at: String,
}

/// 服务端下发的链路配置。字段名是 Java 侧的驼峰形式。
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LinkConfigData {
    pub front: Mapping,
    pub land: Mapping,
    pub expected_egress_ips: Vec<String>,
    pub agent_credentials: Vec<AgentCredentialData>,
    pub ttl_seconds: u64,
}

impl LinkConfigData {
    pub fn into_link_config(self) -> LinkConfig {
        LinkConfig {
            front: self.front,
            land: self.land,
            expected_egress_ips: self.expected_egress_ips,
            agent_credentials: self
                .agent_credentials
                .into_iter()
                .map(|c| crate::link::model::AgentCredential {
                    subscription_id: c.subscription_id,
                    name: c.name,
                    agent_type: c.agent_type,
                    credential: c.credential,
                    ends_at: c.ends_at,
                })
                .collect(),
            ttl_seconds: self.ttl_seconds,
        }
    }
}

pub(crate) fn client() -> reqwest::Client {
    // 拉配置发生在链路建立之前，只能走用户自己的网络
    reqwest::Client::builder()
        .no_proxy()
        .timeout(REQUEST_TIMEOUT)
        .build()
        .expect("构建 HTTP 客户端不应失败")
}

pub async fn fetch_link(base_url: &str, access_token: &str) -> Result<LinkConfig, RemoteError> {
    let resp = client()
        .get(format!("{base_url}/api/link/config"))
        .bearer_auth(access_token)
        .send()
        .await
        .map_err(RemoteError::Request)?;

    if !resp.status().is_success() {
        return Err(RemoteError::Status(resp.status()));
    }

    let body: ApiResponse<LinkConfigData> = resp.json().await.map_err(RemoteError::Request)?;
    Ok(body.into_data()?.into_link_config())
}

pub async fn heartbeat(base_url: &str, access_token: &str) -> Result<LinkStatus, RemoteError> {
    let resp = client()
        .post(format!("{base_url}/api/link/heartbeat"))
        .bearer_auth(access_token)
        .send()
        .await
        .map_err(RemoteError::Request)?;

    if !resp.status().is_success() {
        return Err(RemoteError::Status(resp.status()));
    }

    let body: ApiResponse<HeartbeatData> = resp.json().await.map_err(RemoteError::Request)?;
    Ok(body.into_data()?.status)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 业务码为零时取出数据() {
        let raw = r#"{"code":0,"data":{"status":"ACTIVE"},"msg":null}"#;
        let resp: ApiResponse<HeartbeatData> = serde_json::from_str(raw).unwrap();

        assert_eq!(resp.into_data().unwrap().status, LinkStatus::ACTIVE);
    }

    #[test]
    fn 业务码非零时转为错误并带上服务端文案() {
        // HTTP 是 200，成败只看 code——与服务端约定一致
        let raw = r#"{"code":310003,"data":null,"msg":"该用户的链路已被吊销"}"#;
        let resp: ApiResponse<HeartbeatData> = serde_json::from_str(raw).unwrap();

        match resp.into_data() {
            Err(RemoteError::Biz { code, msg }) => {
                assert_eq!(code, 310003);
                assert_eq!(msg, "该用户的链路已被吊销");
            }
            _ => panic!("应当转为业务错误"),
        }
    }

    #[test]
    fn 链路状态按大写下划线取值解析且含四态() {
        for (raw, expected) in [
            ("\"ACTIVE\"", LinkStatus::ACTIVE),
            ("\"SUSPENDED\"", LinkStatus::SUSPENDED),
            ("\"REVOKED\"", LinkStatus::REVOKED),
            ("\"EXPIRED\"", LinkStatus::EXPIRED),
        ] {
            let s: LinkStatus = serde_json::from_str(raw).unwrap();
            assert_eq!(s, expected);
        }
    }

    #[test]
    fn 链路配置能解析多凭据() {
        let raw = r#"{
          "front": {"type":"trojan","server":"us.example.com","port":443},
          "land": {"type":"socks5","server":"77.47.143.6","port":50101},
          "expectedEgressIps": ["77.47.143.6"],
          "agentCredentials": [
            {"subscriptionId":1,"name":"Claude 席位 1","agentType":"CLAUDE","credential":"sk-ant-a","endsAt":"2026-09-18T12:00:00Z"},
            {"subscriptionId":2,"name":"Codex 席位","agentType":"CODEX","credential":"sk-oai-b","endsAt":"2026-10-01T00:00:00Z"}
          ],
          "ttlSeconds": 1800
        }"#;
        let link = serde_json::from_str::<LinkConfigData>(raw).unwrap().into_link_config();

        assert_eq!(link.agent_credentials.len(), 2);
        assert_eq!(link.agent_credentials[0].agent_type, "CLAUDE");
        assert_eq!(link.agent_credentials[0].credential, "sk-ant-a");
        assert_eq!(link.agent_credentials[1].subscription_id, 2);
    }

    #[test]
    fn 未知agent类型不破坏整体解析() {
        // 服务端将来新增 agent 时，旧客户端必须能解析并在使用侧忽略它
        let raw = r#"{"subscriptionId":9,"name":"新玩意","agentType":"FUTURE_AGENT","credential":"x","endsAt":"2027-01-01T00:00:00Z"}"#;
        let c: AgentCredentialData = serde_json::from_str(raw).unwrap();
        assert_eq!(c.agent_type, "FUTURE_AGENT");
    }
}
