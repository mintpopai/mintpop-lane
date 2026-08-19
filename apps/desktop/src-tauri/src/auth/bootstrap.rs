use crate::auth::oidc::OidcConfig;
use crate::link::remote::{ApiResponse, RemoteError};
use serde::Deserialize;
use std::time::Duration;

const REQUEST_TIMEOUT: Duration = Duration::from_secs(15);

/// 桌面端注册的 deep link 回调地址。它由客户端自己的 scheme 决定，服务端不下发。
pub const REDIRECT_URI: &str = "mintpop://callback";

/// 服务端下发的登录接入配置。字段名是 Java 侧的驼峰形式。
/// 三个字段都不是 Option：任一缺失都说明服务端配置有问题，应当当场报错，
/// 而不是拿空串去拼一个必然失败的授权地址。
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientConfigData {
    pub logto_issuer: String,
    pub logto_client_id: String,
    pub api_resource: String,
}

impl ClientConfigData {
    pub fn into_oidc_config(self) -> OidcConfig {
        OidcConfig {
            issuer: self.logto_issuer,
            client_id: self.logto_client_id,
            redirect_uri: REDIRECT_URI.to_string(),
            resource: self.api_resource,
        }
    }
}

fn client() -> reqwest::Client {
    // 引导发生在链路建立之前，只能走用户自己的网络，因此不套本地代理
    reqwest::Client::builder()
        .no_proxy()
        .timeout(REQUEST_TIMEOUT)
        .build()
        .expect("构建 HTTP 客户端不应失败")
}

/// 拉取登录接入配置。这是客户端启动后的第一个网络请求，失败则无法登录。
pub async fn fetch_client_config(base_url: &str) -> Result<OidcConfig, RemoteError> {
    let resp = client()
        .get(format!("{base_url}/api/client-config"))
        .send()
        .await
        .map_err(RemoteError::Request)?;

    if !resp.status().is_success() {
        return Err(RemoteError::Status(resp.status()));
    }

    let body: ApiResponse<ClientConfigData> = resp.json().await.map_err(RemoteError::Request)?;
    Ok(body.into_data()?.into_oidc_config())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 业务码为零时映射成登录配置() {
        let raw = r#"{"code":0,"data":{"logtoIssuer":"https://tenant.logto.app/oidc","logtoClientId":"client-1","apiResource":"https://api.mintpop.internal"},"msg":null}"#;
        let resp: ApiResponse<ClientConfigData> = serde_json::from_str(raw).unwrap();

        let cfg = resp.into_data().unwrap().into_oidc_config();

        assert_eq!(cfg.issuer, "https://tenant.logto.app/oidc");
        assert_eq!(cfg.client_id, "client-1");
        assert_eq!(cfg.resource, "https://api.mintpop.internal");
        // redirect_uri 由客户端持有，与服务端下发的内容无关
        assert_eq!(cfg.redirect_uri, "mintpop://callback");
    }

    #[test]
    fn 业务码非零时转为错误并带上服务端文案() {
        // HTTP 是 200，成败只看 code——与服务端约定一致
        let raw = r#"{"code":110002,"data":null,"msg":"服务内部错误"}"#;
        let resp: ApiResponse<ClientConfigData> = serde_json::from_str(raw).unwrap();

        match resp.into_data() {
            Err(RemoteError::Biz { code, msg }) => {
                assert_eq!(code, 110002);
                assert_eq!(msg, "服务内部错误");
            }
            other => panic!("期望业务错误，实际是 {other:?}"),
        }
    }

    #[test]
    fn 缺字段的返回体解析失败而不是静默用空串() {
        // 服务端漏配 logto-client-id 时会下发 null，此处必须报错
        let raw = r#"{"code":0,"data":{"logtoIssuer":"https://tenant.logto.app/oidc","logtoClientId":null,"apiResource":"https://api.mintpop.internal"},"msg":null}"#;

        let parsed: Result<ApiResponse<ClientConfigData>, _> = serde_json::from_str(raw);

        assert!(parsed.is_err());
    }
}
