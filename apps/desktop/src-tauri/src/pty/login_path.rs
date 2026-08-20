//! 登录 shell 的 PATH 探测。
//!
//! 为什么需要它：macOS 上从 Finder / Dock 启动的 .app，继承的是 launchd 那份精简 PATH
//! （通常只有 /usr/bin:/bin:/usr/sbin:/sbin），它**不读** `~/.zshrc`、`~/.zprofile` 这类
//! dotfiles。用户装在 `/opt/homebrew/bin`、`~/.local/bin`、nvm 目录下的 `claude` / `codex`
//! 因此都不在 PATH 里，`CommandBuilder::new("claude")` 必然 spawn 失败。
//! 开发模式是从终端启动的，继承了完整 PATH，所以这个坑在 dev 下看不见，只有打包后才暴露。
//!
//! 做法：跑一次用户自己的登录交互式 shell（`-lic`），让它把加载完 dotfiles 后的 PATH 打回来。

use std::sync::OnceLock;

/// 输出行的标记前缀。dotfiles 里常有 banner、提示、fortune 之类的杂音一起打到 stdout，
/// 只认带这个前缀的那一行，才能稳定地把 PATH 从噪声里摘出来。
const MARKER: &str = "__LANE_PATH__";

/// 求登录 shell 的 PATH 要真的拉起一个 shell 并加载全套 dotfiles，开销不小，
/// 进程生命周期内只做一次。
static CACHED: OnceLock<Option<String>> = OnceLock::new();

/// 取登录 shell 解析出的 PATH；求不到（或 Windows 上无此必要）时返回 None，
/// 调用方回退到当前进程的 PATH。
pub fn login_shell_path() -> Option<String> {
    CACHED.get_or_init(probe).clone()
}

/// 非 Windows：用 `$SHELL`（缺省 `/bin/zsh`）跑一次 `-lic`，取带标记那一行。
#[cfg(not(windows))]
fn probe() -> Option<String> {
    let shell = std::env::var("SHELL").unwrap_or_else(|_| "/bin/zsh".to_string());
    let script = format!("echo {MARKER}$PATH");
    let output = std::process::Command::new(shell)
        .args(["-lic", script.as_str()])
        .output()
        .ok()?;

    String::from_utf8_lossy(&output.stdout)
        .lines()
        .find_map(|line| line.trim().strip_prefix(MARKER))
        .map(str::to_string)
        .filter(|path| !path.is_empty())
}

/// Windows 的 GUI 进程本就继承完整的用户环境变量，不存在 launchd 那种精简 PATH，
/// 不必绕道 shell 求解。
#[cfg(windows)]
fn probe() -> Option<String> {
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(not(windows))]
    #[test]
    fn 登录shell解析出的path包含系统目录() {
        let path = login_shell_path().expect("非 Windows 下应当能求出登录 shell 的 PATH");
        assert!(path.contains("/usr/bin"), "实际取到的 PATH：{path}");
    }

    #[test]
    fn 两次调用取到同一份缓存() {
        // 值相等 + 底层是同一块缓存，确认没有每次都去拉起 shell
        assert_eq!(login_shell_path(), login_shell_path());
        assert!(std::ptr::eq(
            CACHED.get_or_init(probe),
            CACHED.get_or_init(probe)
        ));
    }
}
