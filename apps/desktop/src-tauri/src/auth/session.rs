use crate::link::remote::{client, ApiResponse, RemoteError};
use serde::{Deserialize, Serialize};
use thiserror::Error;
use url::Url;

#[derive(Debug, Error)]
pub enum SessionError {
    #[error("回调地址解析失败：{0}")]
    BadCallback(String),
    #[error("回调的 state 与本次登录不匹配")]
    StateMismatch,
    #[error("回调中没有 ticket")]
    NoTicket,
}

/// 深链回调的两种合法结局：拿到票，或服务端明确告知登录失败
#[allow(non_camel_case_types)]
#[derive(Debug, PartialEq, Eq)]
pub enum CallbackOutcome {
    TICKET(String),
    LOGIN_FAILED,
}

/// 桌面端会话兑换的返回体，字段与服务端 DesktopSessionResponse 逐字对应
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopSessionData {
    token: String,
    #[allow(dead_code)]
    expires_in_seconds: u64,
}

/// /api/me 的返回体。不含任何凭据，可原样交给渲染层做状态页。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MeData {
    pub id: i64,
    pub email: String,
    pub name: String,
    pub role: String,
    pub subscriptions: Vec<MeSubscription>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MeSubscription {
    pub id: i64,
    pub name: String,
    pub agent_type: String,
    pub starts_at: String,
    pub ends_at: String,
    /// 服务端按当前时间算好的在期标记
    pub active: bool,
}

/// 构造服务端登录入口地址。PKCE 对象从 Logto 换成了自家服务端，
/// verifier 仍从不离开本进程内存——窃得深链 ticket 的人没有它兑不出会话。
pub fn build_start_url(base: &str, challenge: &str, state: &str) -> String {
    // base 来自编译期注入的 LANE_SERVER_URL，是可信配置，解析失败属构建错误
    let mut url = Url::parse(&format!("{base}/auth/desktop/start")).expect("服务端地址应当合法");
    url.query_pairs_mut()
        .append_pair("code_challenge", challenge)
        .append_pair("state", state);
    url.to_string()
}

/// 从深链回调取出 ticket，并校验 state（防 CSRF：不是本次登录发起的回调一律不认）
pub fn extract_ticket(callback: &str, expected_state: &str) -> Result<CallbackOutcome, SessionError> {
    let url = Url::parse(callback).map_err(|e| SessionError::BadCallback(e.to_string()))?;

    let mut ticket = None;
    let mut state = None;
    let mut failed = false;
    for (k, v) in url.query_pairs() {
        match k.as_ref() {
            "ticket" => ticket = Some(v.to_string()),
            "state" => state = Some(v.to_string()),
            "error" => failed = true,
            _ => {}
        }
    }

    if state.as_deref() != Some(expected_state) {
        return Err(SessionError::StateMismatch);
    }
    if failed {
        return Ok(CallbackOutcome::LOGIN_FAILED);
    }
    ticket.map(CallbackOutcome::TICKET).ok_or(SessionError::NoTicket)
}

/// 用一次性 ticket + PKCE verifier 兑换 30 天自签会话 token
pub async fn exchange_ticket(base: &str, ticket: &str, verifier: &str) -> Result<String, RemoteError> {
    let resp = client()
        .post(format!("{base}/api/auth/desktop/exchange"))
        .json(&serde_json::json!({ "ticket": ticket, "verifier": verifier }))
        .send()
        .await
        .map_err(RemoteError::Request)?;

    if !resp.status().is_success() {
        return Err(RemoteError::Status(resp.status()));
    }
    let body: ApiResponse<DesktopSessionData> = resp.json().await.map_err(RemoteError::Request)?;
    Ok(body.into_data()?.token)
}

/// 拉当前用户信息。启动时用它验活钥匙串里的会话 token，登录后用它渲染状态页。
pub async fn fetch_me(base: &str, token: &str) -> Result<MeData, RemoteError> {
    let resp = client()
        .get(format!("{base}/api/me"))
        .bearer_auth(token)
        .send()
        .await
        .map_err(RemoteError::Request)?;

    if !resp.status().is_success() {
        return Err(RemoteError::Status(resp.status()));
    }
    let body: ApiResponse<MeData> = resp.json().await.map_err(RemoteError::Request)?;
    body.into_data()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 登录入口地址带上挑战串与state() {
        let url = build_start_url("https://api.example.com", "A".repeat(43).as_str(), "st-1");
        assert!(url.starts_with("https://api.example.com/auth/desktop/start?"));
        assert!(url.contains(&format!("code_challenge={}", "A".repeat(43))));
        assert!(url.contains("state=st-1"));
    }

    #[test]
    fn 正常回调取出ticket() {
        let out = extract_ticket("lane://callback?ticket=t-abc&state=st-1", "st-1").unwrap();
        assert_eq!(out, CallbackOutcome::TICKET("t-abc".to_string()));
    }

    #[test]
    fn state不匹配一律拒绝() {
        // 防反向攻击：别人把「攻击者账号的 ticket」塞给你，骗你登进他的会话
        let r = extract_ticket("lane://callback?ticket=t&state=别人的", "st-1");
        assert!(matches!(r, Err(SessionError::StateMismatch)));
    }

    #[test]
    fn 服务端明示失败时返回失败结局() {
        let out = extract_ticket("lane://callback?error=login_failed&state=st-1", "st-1").unwrap();
        assert_eq!(out, CallbackOutcome::LOGIN_FAILED);
    }

    #[test]
    fn 无ticket无error视为非法回调() {
        let r = extract_ticket("lane://callback?state=st-1", "st-1");
        assert!(matches!(r, Err(SessionError::NoTicket)));
    }

    #[test]
    fn 兑换返回体能解析出token() {
        let raw = r#"{"code":0,"data":{"token":"jwt-abc","expiresInSeconds":2592000},"msg":null}"#;
        let resp: ApiResponse<DesktopSessionData> = serde_json::from_str(raw).unwrap();
        assert_eq!(resp.into_data().unwrap().token, "jwt-abc");
    }

    #[test]
    fn me返回体能解析订阅列表() {
        let raw = r#"{"code":0,"data":{"id":7,"email":"a@b.c","name":"甲","role":"MEMBER",
          "subscriptions":[{"id":1,"name":"Claude 席位","agentType":"CLAUDE",
            "startsAt":"2026-08-01T00:00:00","endsAt":"2026-09-01T00:00:00","active":true}]},"msg":null}"#;
        let resp: ApiResponse<MeData> = serde_json::from_str(raw).unwrap();
        let me = resp.into_data().unwrap();
        assert_eq!(me.email, "a@b.c");
        assert!(me.subscriptions[0].active);
        assert_eq!(me.subscriptions[0].agent_type, "CLAUDE");
    }
}
