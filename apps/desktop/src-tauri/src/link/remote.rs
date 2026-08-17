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

/// 员工状态，与服务端 EmployeeStatus 逐字对应
#[allow(non_camel_case_types)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
pub enum EmployeeStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED,
}

#[derive(Debug, Deserialize)]
pub struct HeartbeatData {
    pub status: EmployeeStatus,
}

/// 服务端下发的链路配置。字段名是 Java 侧的驼峰形式。
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LinkConfigData {
    pub front: Mapping,
    pub land: Mapping,
    pub expected_egress_ips: Vec<String>,
    pub claude_credential: String,
    pub ttl_seconds: u64,
}

impl LinkConfigData {
    pub fn into_link_config(self) -> LinkConfig {
        LinkConfig {
            front: self.front,
            land: self.land,
            expected_egress_ips: self.expected_egress_ips,
            claude_credential: self.claude_credential,
            ttl_seconds: self.ttl_seconds,
        }
    }
}

fn client() -> reqwest::Client {
    // 拉配置发生在链路建立之前，只能走员工自己的网络
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

pub async fn heartbeat(base_url: &str, access_token: &str) -> Result<EmployeeStatus, RemoteError> {
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

        assert_eq!(resp.into_data().unwrap().status, EmployeeStatus::ACTIVE);
    }

    #[test]
    fn 业务码非零时转为错误并带上服务端文案() {
        // HTTP 是 200，成败只看 code——与服务端约定一致
        let raw = r#"{"code":310003,"data":null,"msg":"该员工的链路已被吊销"}"#;
        let resp: ApiResponse<HeartbeatData> = serde_json::from_str(raw).unwrap();

        match resp.into_data() {
            Err(RemoteError::Biz { code, msg }) => {
                assert_eq!(code, 310003);
                assert_eq!(msg, "该员工的链路已被吊销");
            }
            _ => panic!("应当转为业务错误"),
        }
    }

    #[test]
    fn 员工状态按大写下划线取值解析() {
        let s: EmployeeStatus = serde_json::from_str("\"REVOKED\"").unwrap();
        assert_eq!(s, EmployeeStatus::REVOKED);
    }

    #[test]
    fn 链路配置能从服务端返回体解析() {
        let raw = r#"{
          "front": {"type":"trojan","server":"us.example.com","port":443},
          "land": {"type":"socks5","server":"77.47.143.6","port":50101},
          "expectedEgressIps": ["77.47.143.6"],
          "claudeCredential": "sk-ant-test",
          "ttlSeconds": 1800
        }"#;
        let data: LinkConfigData = serde_json::from_str(raw).unwrap();
        let link = data.into_link_config();

        assert_eq!(link.expected_egress_ips, vec!["77.47.143.6".to_string()]);
        assert_eq!(link.claude_credential, "sk-ant-test");
        assert_eq!(link.front["server"].as_str().unwrap(), "us.example.com");
    }
}
