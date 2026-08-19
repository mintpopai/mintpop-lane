use keyring::Entry;
use thiserror::Error;

const SERVICE: &str = "ai.mintpop.pier";
const ACCOUNT: &str = "logto-refresh-token";

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("访问系统钥匙串失败：{0}")]
    Keyring(#[from] keyring::Error),
}

fn entry() -> Result<Entry, StorageError> {
    Ok(Entry::new(SERVICE, ACCOUNT)?)
}

/// refresh_token 是唯一允许持久化的凭据，且只能进 OS 钥匙串。
/// 链路节点与 Claude 席位凭据一律只存内存。
pub fn save_refresh_token(token: &str) -> Result<(), StorageError> {
    entry()?.set_password(token)?;
    Ok(())
}

pub fn load_refresh_token() -> Result<Option<String>, StorageError> {
    match entry()?.get_password() {
        Ok(token) => Ok(Some(token)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(StorageError::Keyring(e)),
    }
}

pub fn clear_refresh_token() -> Result<(), StorageError> {
    match entry()?.delete_credential() {
        Ok(()) => Ok(()),
        Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(StorageError::Keyring(e)),
    }
}

// 本模块不写单元测试：行为完全由系统钥匙串决定，在无头 CI 里必然失败，
// 测了也只是在测 keyring 自己。其正确性由登录流程的手工验收覆盖。
