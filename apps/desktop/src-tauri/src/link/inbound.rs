use crate::link::model::InboundCredentials;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use rand::Rng;
use std::net::TcpListener;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum InboundError {
    #[error("在高位端口区间内找不到可用端口")]
    NoFreePort,
}

const PORT_MIN: u16 = 20000;
const PORT_MAX: u16 = 60000;
const MAX_ATTEMPTS: u32 = 64;

/// 在高位区间随机试探一个空闲端口。
/// 试绑后立即释放，存在极小的竞争窗口，由内核启动失败时重试来兜底。
pub fn allocate_free_port() -> Result<u16, InboundError> {
    let mut rng = rand::thread_rng();
    for _ in 0..MAX_ATTEMPTS {
        let port = rng.gen_range(PORT_MIN..=PORT_MAX);
        if TcpListener::bind(("127.0.0.1", port)).is_ok() {
            return Ok(port);
        }
    }
    Err(InboundError::NoFreePort)
}

/// 生成 URL 安全的随机串，避免 @ : / 等字符破坏代理地址
fn random_token(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    rand::thread_rng().fill(&mut buf[..]);
    URL_SAFE_NO_PAD.encode(buf)
}

/// 分配本次启动使用的入站端口与一次一密的凭据，不持久化
pub fn allocate_inbound() -> Result<InboundCredentials, InboundError> {
    Ok(InboundCredentials {
        port: allocate_free_port()?,
        username: random_token(12),
        password: random_token(24),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 分配的端口落在高位区间且可用() {
        let port = allocate_free_port().unwrap();
        assert!((20000..=60000).contains(&port), "端口 {port} 不在高位区间");
        // 既然报告为空闲，就应该能绑上
        std::net::TcpListener::bind(("127.0.0.1", port)).expect("端口应当可绑定");
    }

    #[test]
    fn 每次分配的凭据都不相同() {
        let a = allocate_inbound().unwrap();
        let b = allocate_inbound().unwrap();
        assert_ne!(a.password, b.password);
        assert_ne!(a.username, b.username);
    }

    #[test]
    fn 凭据具备足够长度且不含会破坏代理地址的字符() {
        let c = allocate_inbound().unwrap();
        assert!(c.password.len() >= 32);
        for ch in ['@', ':', '/', ' '] {
            assert!(!c.username.contains(ch), "用户名不应含 {ch}");
            assert!(!c.password.contains(ch), "密码不应含 {ch}");
        }
    }

    #[test]
    fn 代理地址只指向回环() {
        let c = allocate_inbound().unwrap();
        assert!(c.proxy_url().starts_with("http://"));
        assert!(c.proxy_url().contains("@127.0.0.1:"));
    }
}
