use crate::auth::pkce::PkcePair;
use serde::Deserialize;
use thiserror::Error;
use url::Url;

#[derive(Debug, Clone)]
pub struct OidcConfig {
    /// Logto 租户的 OIDC issuer，形如 https://<tenant>.logto.app/oidc
    pub issuer: String,
    pub client_id: String,
    pub redirect_uri: String,
    /// 本服务在 Logto 注册的 API Resource 标识
    pub resource: String,
}

#[derive(Debug, Deserialize)]
pub struct TokenSet {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_in: Option<u64>,
}

#[derive(Debug, Error)]
pub enum OidcError {
    #[error("回调地址解析失败：{0}")]
    BadCallback(String),
    #[error("回调的 state 与本次登录不匹配")]
    StateMismatch,
    #[error("回调中没有授权码")]
    NoCode,
    #[error("令牌请求失败：{0}")]
    Request(#[source] reqwest::Error),
    #[error("令牌端点返回错误状态：{0}")]
    Status(reqwest::StatusCode),
}

/// 构造授权地址。
/// 带 resource 才能拿到 audience 为本服务的 access_token；
/// 带 offline_access 才能拿到 refresh_token，用于下次静默登录。
pub fn build_authorize_url(cfg: &OidcConfig, pkce: &PkcePair, state: &str) -> String {
    // issuer 的合法性已在 bootstrap::ClientConfigData::into_oidc_config 里校验过，
    // 走到这里的 OidcConfig 一定是合法地址，故此处的 expect 是真正的不变量断言，
    // 而不是对未经校验的远端输入直接 panic。
    let mut url = Url::parse(&format!("{}/auth", cfg.issuer)).expect("issuer 应当是合法地址");
    url.query_pairs_mut()
        .append_pair("client_id", &cfg.client_id)
        .append_pair("redirect_uri", &cfg.redirect_uri)
        .append_pair("response_type", "code")
        .append_pair("scope", "openid profile offline_access")
        .append_pair("resource", &cfg.resource)
        .append_pair("prompt", "consent")
        .append_pair("state", state)
        .append_pair("code_challenge", &pkce.challenge)
        .append_pair("code_challenge_method", "S256");

    url.to_string()
}

/// 从 deep link 回调里取出授权码，并校验 state
pub fn extract_code(callback: &str, expected_state: &str) -> Result<String, OidcError> {
    let url = Url::parse(callback).map_err(|e| OidcError::BadCallback(e.to_string()))?;

    let mut code = None;
    let mut state = None;
    for (k, v) in url.query_pairs() {
        match k.as_ref() {
            "code" => code = Some(v.to_string()),
            "state" => state = Some(v.to_string()),
            _ => {}
        }
    }

    // 防 CSRF：不是本次登录发起的回调一律不认
    if state.as_deref() != Some(expected_state) {
        return Err(OidcError::StateMismatch);
    }
    code.ok_or(OidcError::NoCode)
}

fn token_client() -> reqwest::Client {
    // 登录发生在链路建立之前，只能走用户自己的网络，因此不套本地代理
    reqwest::Client::builder()
        .no_proxy()
        .build()
        .expect("构建 HTTP 客户端不应失败")
}

/// 用授权码换令牌
pub async fn exchange_code(
    cfg: &OidcConfig,
    code: &str,
    verifier: &str,
) -> Result<TokenSet, OidcError> {
    let params = [
        ("grant_type", "authorization_code"),
        ("client_id", cfg.client_id.as_str()),
        ("redirect_uri", cfg.redirect_uri.as_str()),
        ("code", code),
        ("code_verifier", verifier),
        ("resource", cfg.resource.as_str()),
    ];
    post_token(cfg, &params).await
}

/// 用 refresh_token 换新的 access_token，用于静默登录与续期
pub async fn refresh(cfg: &OidcConfig, refresh_token: &str) -> Result<TokenSet, OidcError> {
    let params = [
        ("grant_type", "refresh_token"),
        ("client_id", cfg.client_id.as_str()),
        ("refresh_token", refresh_token),
        ("resource", cfg.resource.as_str()),
    ];
    post_token(cfg, &params).await
}

async fn post_token(cfg: &OidcConfig, params: &[(&str, &str)]) -> Result<TokenSet, OidcError> {
    let resp = token_client()
        .post(format!("{}/token", cfg.issuer))
        .form(params)
        .send()
        .await
        .map_err(OidcError::Request)?;

    if !resp.status().is_success() {
        return Err(OidcError::Status(resp.status()));
    }
    resp.json::<TokenSet>().await.map_err(OidcError::Request)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::pkce;

    fn sample_config() -> OidcConfig {
        OidcConfig {
            issuer: "https://tenant.logto.app/oidc".to_string(),
            client_id: "client-1".to_string(),
            redirect_uri: "mintpop://callback".to_string(),
            resource: "https://api.mintpop.internal".to_string(),
        }
    }

    #[test]
    fn 授权地址包含pkce与资源标识() {
        let pair = pkce::generate();
        let url = build_authorize_url(&sample_config(), &pair, "state-1");

        assert!(url.starts_with("https://tenant.logto.app/oidc/auth?"));
        assert!(url.contains("response_type=code"));
        assert!(url.contains("code_challenge_method=S256"));
        assert!(url.contains(&format!(
            "code_challenge={}",
            urlencoding_like(&pair.challenge)
        )));
        assert!(url.contains("state=state-1"));
        // 必须带 resource，否则拿到的 access_token 没有本服务的 audience
        assert!(url.contains("resource=https%3A%2F%2Fapi.mintpop.internal"));
        // 必须申请 offline_access，否则拿不到 refresh_token
        assert!(url.contains("offline_access"));
    }

    /// challenge 是 URL-safe base64，只有 - 和 _ 两种特殊字符，
    /// query_pairs_mut 不会对它们转义，故原样返回即可
    fn urlencoding_like(s: &str) -> String {
        s.to_string()
    }

    #[test]
    fn 能从回调地址里取出授权码() {
        let code = extract_code("mintpop://callback?code=abc123&state=state-1", "state-1").unwrap();
        assert_eq!(code, "abc123");
    }

    #[test]
    fn state不匹配的回调被拒绝() {
        // 防 CSRF：不是本次登录发起的回调一律不认
        assert!(matches!(
            extract_code("mintpop://callback?code=abc123&state=别的", "state-1"),
            Err(OidcError::StateMismatch)
        ));
    }

    #[test]
    fn 没有授权码的回调被拒绝() {
        assert!(matches!(
            extract_code("mintpop://callback?error=access_denied&state=state-1", "state-1"),
            Err(OidcError::NoCode)
        ));
    }
}
