use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use rand::Rng;
use sha2::{Digest, Sha256};

/// PKCE 的验证串与挑战串。
/// 桌面端是 public client，无法安全保存 client secret，必须用 PKCE 保护授权码交换。
pub struct PkcePair {
    pub verifier: String,
    pub challenge: String,
}

/// 由验证串算出挑战串（S256）
pub fn challenge_of(verifier: &str) -> String {
    let digest = Sha256::digest(verifier.as_bytes());
    URL_SAFE_NO_PAD.encode(digest)
}

pub fn generate() -> PkcePair {
    // 48 字节经 URL-safe base64 编码后是 64 个字符，落在 RFC 7636 要求的 43-128 之间
    let mut buf = [0u8; 48];
    rand::thread_rng().fill(&mut buf[..]);
    let verifier = URL_SAFE_NO_PAD.encode(buf);
    let challenge = challenge_of(&verifier);

    PkcePair { verifier, challenge }
}

/// 生成防 CSRF 的 state
pub fn random_state() -> String {
    let mut buf = [0u8; 16];
    rand::thread_rng().fill(&mut buf[..]);
    URL_SAFE_NO_PAD.encode(buf)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 每次生成的验证串都不同() {
        assert_ne!(generate().verifier, generate().verifier);
    }

    #[test]
    fn 验证串长度符合规范要求() {
        // RFC 7636 要求 43-128 个字符
        let pair = generate();
        assert!((43..=128).contains(&pair.verifier.len()));
    }

    #[test]
    fn 挑战串是验证串的sha256且为url安全base64() {
        let pair = generate();
        assert!(!pair.challenge.contains('='));
        assert!(!pair.challenge.contains('+'));
        assert!(!pair.challenge.contains('/'));
        // 同一验证串重复计算结果稳定
        assert_eq!(pair.challenge, challenge_of(&pair.verifier));
    }

    #[test]
    fn 每次生成的state都不同() {
        assert_ne!(random_state(), random_state());
    }
}
