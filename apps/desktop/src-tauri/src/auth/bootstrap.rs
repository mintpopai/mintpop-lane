use crate::auth::oidc::OidcConfig;
use crate::link::remote::{ApiResponse, RemoteError};
use serde::Deserialize;
use std::time::Duration;
use url::Url;

const REQUEST_TIMEOUT: Duration = Duration::from_secs(15);

/// 桌面端注册的 deep link 回调地址。它由客户端自己的 scheme 决定，服务端不下发。
pub const REDIRECT_URI: &str = "lane://callback";

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
    /// 服务端下发的字段只保证了 JSON 里"存在"，不保证内容合法（空串、缺协议头的地址
    /// 都能正常反序列化）。这里做一次形状校验，把畸形配置挡在网络层之外——
    /// 否则拼授权地址时会在 `Url::parse(...).expect(...)` 处直接 panic 整个客户端。
    pub fn into_oidc_config(self) -> Result<OidcConfig, RemoteError> {
        let issuer = self.logto_issuer.trim();
        if issuer.is_empty() {
            return Err(RemoteError::InvalidConfig("logtoIssuer 为空".to_string()));
        }
        let client_id = self.logto_client_id.trim();
        if client_id.is_empty() {
            return Err(RemoteError::InvalidConfig("logtoClientId 为空".to_string()));
        }
        let resource = self.api_resource.trim();
        if resource.is_empty() {
            return Err(RemoteError::InvalidConfig("apiResource 为空".to_string()));
        }

        let issuer_url = Url::parse(issuer)
            .map_err(|e| RemoteError::InvalidConfig(format!("logtoIssuer 不是合法地址：{e}")))?;

        // 生产环境要求 https；http 只在 localhost/127.0.0.1 上放行——这是给「本地自建
        // Logto 走 http 联调」留的口子，不是普遍允许明文协议。
        let scheme_ok = match issuer_url.scheme() {
            "https" => true,
            "http" => matches!(issuer_url.host_str(), Some("localhost") | Some("127.0.0.1")),
            _ => false,
        };
        if !scheme_ok {
            return Err(RemoteError::InvalidConfig(format!(
                "logtoIssuer 协议非法（仅允许 https，或本地开发用 http://localhost、http://127.0.0.1）：{issuer}"
            )));
        }

        Ok(OidcConfig {
            issuer: issuer.to_string(),
            client_id: client_id.to_string(),
            redirect_uri: REDIRECT_URI.to_string(),
            resource: resource.to_string(),
        })
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
    body.into_data()?.into_oidc_config()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 业务码为零时映射成登录配置() {
        let raw = r#"{"code":0,"data":{"logtoIssuer":"https://tenant.logto.app/oidc","logtoClientId":"client-1","apiResource":"https://api.lane.mintpop.internal"},"msg":null}"#;
        let resp: ApiResponse<ClientConfigData> = serde_json::from_str(raw).unwrap();

        let cfg = resp.into_data().unwrap().into_oidc_config().unwrap();

        assert_eq!(cfg.issuer, "https://tenant.logto.app/oidc");
        assert_eq!(cfg.client_id, "client-1");
        assert_eq!(cfg.resource, "https://api.lane.mintpop.internal");
        // redirect_uri 由客户端持有，与服务端下发的内容无关
        assert_eq!(cfg.redirect_uri, "lane://callback");
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
        let raw = r#"{"code":0,"data":{"logtoIssuer":"https://tenant.logto.app/oidc","logtoClientId":null,"apiResource":"https://api.lane.mintpop.internal"},"msg":null}"#;

        let parsed: Result<ApiResponse<ClientConfigData>, _> = serde_json::from_str(raw);

        assert!(parsed.is_err());
    }

    fn sample_data(issuer: &str, client_id: &str) -> ClientConfigData {
        ClientConfigData {
            logto_issuer: issuer.to_string(),
            logto_client_id: client_id.to_string(),
            api_resource: "https://api.lane.mintpop.internal".to_string(),
        }
    }

    #[test]
    fn 合法的https_issuer校验通过() {
        let cfg = sample_data("https://tenant.logto.app/oidc", "client-1")
            .into_oidc_config()
            .unwrap();
        assert_eq!(cfg.issuer, "https://tenant.logto.app/oidc");
    }

    #[test]
    fn 缺协议头的issuer被拒绝() {
        // 真实的运维手滑：漏写 https:// 前缀
        let err = sample_data("tenant.logto.app/oidc", "client-1")
            .into_oidc_config()
            .unwrap_err();
        assert!(matches!(err, RemoteError::InvalidConfig(_)));
    }

    #[test]
    fn 非本地的http_issuer被拒绝() {
        // http 只给本地自建 Logto 联调放行，公网地址必须 https
        let err = sample_data("http://tenant.logto.app/oidc", "client-1")
            .into_oidc_config()
            .unwrap_err();
        assert!(matches!(err, RemoteError::InvalidConfig(_)));
    }

    #[test]
    fn 本地回环地址的http_issuer被放行() {
        // 本地自建 Logto 走 http 联调，是刻意开的口子
        let cfg = sample_data("http://127.0.0.1:3001/oidc", "client-1")
            .into_oidc_config()
            .unwrap();
        assert_eq!(cfg.issuer, "http://127.0.0.1:3001/oidc");
    }

    #[test]
    fn 空的client_id被拒绝() {
        let err = sample_data("https://tenant.logto.app/oidc", "")
            .into_oidc_config()
            .unwrap_err();
        assert!(matches!(err, RemoteError::InvalidConfig(_)));
    }
}
