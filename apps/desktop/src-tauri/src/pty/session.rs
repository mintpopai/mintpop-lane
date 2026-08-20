use crate::link::model::InboundCredentials;
use crate::link::state::LinkState;
use crate::pty::env::build_agent_env;
use crate::pty::login_path::login_shell_path;
use portable_pty::{native_pty_system, CommandBuilder, MasterPty, PtySize};
use std::io::{Read, Write};
use std::sync::Mutex;
use thiserror::Error;

#[allow(non_camel_case_types)]
#[derive(Debug, Error)]
pub enum SpawnError {
    #[error("链路不可用（当前状态：{0:?}），拒绝启动 Agent")]
    LINK_NOT_ACTIVE(LinkState),
    #[error("创建 PTY 失败：{0}")]
    PTY_FAILED(String),
    #[error("workspace 目录不存在或不可用：{0}")]
    BAD_WORKSPACE(String),
    #[error("未找到 {0} 命令，请先安装对应的 agent CLI")]
    AGENT_NOT_FOUND(String),
}

/// 在给定 PATH 里探测命令是否可执行。
/// 命令名里已经含路径分隔符时视为具体路径，直接查该文件，不做 PATH 搜索。
fn command_exists(command: &str, path: &str) -> bool {
    let has_separator = command.contains('/') || (cfg!(windows) && command.contains('\\'));
    if has_separator {
        return std::path::Path::new(command).is_file();
    }

    // Windows 上的 CLI 常以 .cmd（npm 包装器）或 .exe 落地，裸名字查不到
    let candidates: Vec<String> = if cfg!(windows) {
        vec![
            command.to_string(),
            format!("{command}.cmd"),
            format!("{command}.exe"),
        ]
    } else {
        vec![command.to_string()]
    };

    std::env::split_paths(path)
        .any(|dir| candidates.iter().any(|name| dir.join(name).is_file()))
}

pub struct PtySession {
    master: Box<dyn MasterPty + Send>,
    writer: Mutex<Box<dyn Write + Send>>,
    child: Mutex<Box<dyn portable_pty::Child + Send + Sync>>,
}

impl PtySession {
    /// 向子进程写入字节（前端键盘输入）
    pub fn write(&self, bytes: &[u8]) -> Result<(), SpawnError> {
        let mut w = self
            .writer
            .lock()
            .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))?;
        w.write_all(bytes)
            .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))?;
        w.flush().map_err(|e| SpawnError::PTY_FAILED(e.to_string()))
    }

    /// 取一个读端，用于把子进程输出泵到前端
    pub fn reader(&self) -> Result<Box<dyn Read + Send>, SpawnError> {
        self.master
            .try_clone_reader()
            .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))
    }

    /// 窗口尺寸变化时同步给子进程，TUI 才能正确重绘
    pub fn resize(&self, rows: u16, cols: u16) -> Result<(), SpawnError> {
        self.master
            .resize(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))
    }

    /// 结束子进程。用户关闭会话时调用；不依赖 Drop——portable-pty 的 Child
    /// 被丢弃时不保证杀死子进程，显式 kill 才能避免孤儿进程继续占着注入的凭据。
    pub fn kill(&self) {
        if let Ok(mut child) = self.child.lock() {
            let _ = child.kill();
        }
    }
}

/// 启动 Agent 子进程的唯一入口。
/// 任何子进程都必须经过这里，注入在此发生，前端无法绕过。
/// 参数比 clippy 默认阈值（7）多一个：会话级注入引入 workspace 后签名自然变长，
/// 拆结构体反而会让调用点变得绕，权衡后选择放行而非强行合并参数。
#[allow(clippy::too_many_arguments)]
pub fn spawn_agent_pty(
    state: LinkState,
    inbound: &InboundCredentials,
    command: &str,
    credential_env: &str,
    credential: &str,
    workspace: &std::path::Path,
    rows: u16,
    cols: u16,
) -> Result<PtySession, SpawnError> {
    // 第一道守卫：链路不活跃就直接拒绝，绝不以直连方式放行
    if !state.allows_spawn() {
        return Err(SpawnError::LINK_NOT_ACTIVE(state));
    }

    if !workspace.is_dir() {
        return Err(SpawnError::BAD_WORKSPACE(workspace.display().to_string()));
    }

    // 打包后的 .app 只继承 launchd 的精简 PATH，找不到用户装的 agent CLI，
    // 故先求登录 shell 的 PATH；求不到时退回当前进程的 PATH。
    let login_path = login_shell_path();
    let search_path = login_path
        .clone()
        .or_else(|| std::env::var("PATH").ok())
        .unwrap_or_default();

    // 命令不存在就当场给出可读原因，别让用户只看到一个 PTY spawn 的底层报错
    if !command_exists(command, &search_path) {
        return Err(SpawnError::AGENT_NOT_FOUND(command.to_string()));
    }

    let pty_system = native_pty_system();
    let pair = pty_system
        .openpty(PtySize {
            rows,
            cols,
            pixel_width: 0,
            pixel_height: 0,
        })
        .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))?;

    // 会话载体从裸 shell 变为在 workspace 里直接运行 agent CLI
    let mut cmd = CommandBuilder::new(command);
    cmd.cwd(workspace);
    for (key, value) in build_agent_env(inbound, credential_env, credential) {
        cmd.env(key, value);
    }
    // 把登录 shell 的 PATH 传给子进程：agent CLI 自己还会去调 git、node 等工具，
    // 只解析出主命令还不够，整条 PATH 都得对齐用户终端里的样子
    if let Some(path) = login_path {
        cmd.env("PATH", path);
    }

    let child = pair
        .slave
        .spawn_command(cmd)
        .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))?;

    let writer = pair
        .master
        .take_writer()
        .map_err(|e| SpawnError::PTY_FAILED(e.to_string()))?;

    Ok(PtySession {
        master: pair.master,
        writer: Mutex::new(writer),
        child: Mutex::new(child),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::link::model::InboundCredentials;
    use crate::link::state::LinkState;

    fn sample_inbound() -> InboundCredentials {
        InboundCredentials {
            port: 27890,
            username: "u".to_string(),
            password: "p".to_string(),
        }
    }

    #[test]
    fn 非活跃状态一律拒绝启动子进程() {
        // 安全不变量：链路不可用时宁可不能干活，也绝不放行任何子进程
        for state in [
            LinkState::DISCONNECTED,
            LinkState::CONNECTING,
            LinkState::DEGRADED,
            LinkState::REVOKED,
        ] {
            let result = spawn_agent_pty(
                state,
                &sample_inbound(),
                "/bin/sh",
                "CLAUDE_CODE_OAUTH_TOKEN",
                "tok",
                std::path::Path::new("/tmp"),
                24,
                80,
            );
            match result {
                Err(SpawnError::LINK_NOT_ACTIVE(s)) => assert_eq!(s, state),
                _ => panic!("状态 {state:?} 下不应放行"),
            }
        }
    }

    #[test]
    fn workspace不存在时拒绝启动() {
        let result = spawn_agent_pty(
            LinkState::ACTIVE,
            &sample_inbound(),
            "claude",
            "CLAUDE_CODE_OAUTH_TOKEN",
            "tok",
            std::path::Path::new("/绝不存在的目录/xyz"),
            24,
            80,
        );
        assert!(matches!(result, Err(SpawnError::BAD_WORKSPACE(_))));
    }

    #[test]
    fn 命令不存在时给出可读的未安装提示() {
        // agent CLI 没装（或不在 PATH 上）时应当明确告知，而不是抛一个 PTY 底层错误
        let workspace = tempfile::tempdir().unwrap();
        let result = spawn_agent_pty(
            LinkState::ACTIVE,
            &sample_inbound(),
            "lane-绝不存在的命令-xyz",
            "CLAUDE_CODE_OAUTH_TOKEN",
            "tok",
            workspace.path(),
            24,
            80,
        );
        match result {
            Err(SpawnError::AGENT_NOT_FOUND(name)) => assert_eq!(name, "lane-绝不存在的命令-xyz"),
            Err(e) => panic!("应为 AGENT_NOT_FOUND，实际是 {e:?}"),
            Ok(_) => panic!("不存在的命令不应放行"),
        }
    }

    #[test]
    fn 带路径分隔符的命令直接按文件存在性判定() {
        // /bin/sh 这类绝对路径不该走 PATH 搜索，直接查文件即可
        assert!(command_exists("/bin/sh", ""));
        assert!(!command_exists("/绝不存在的目录/sh", ""));
    }

    #[test]
    fn 活跃状态下可以启动子进程且注入了代理变量() {
        // 用 /bin/sh 而非 agent 命令：本用例验的是「注入的环境变量有没有传进子进程」，
        // 这与跑哪个命令无关。交互式 zsh 会读用户的 ~/.zshrc，既会往 PTY 里吐提示符与
        // 终端标题转义序列干扰断言，也会让结果取决于每个人的 dotfiles。
        let workspace = tempfile::tempdir().unwrap();
        let session = spawn_agent_pty(
            LinkState::ACTIVE,
            &sample_inbound(),
            "/bin/sh",
            "CLAUDE_CODE_OAUTH_TOKEN",
            "tok",
            workspace.path(),
            24,
            80,
        )
        .expect("活跃状态应当放行");

        // PTY 是流，一次 read 只拿到当时缓冲区里的一段。用后台线程持续泵读、主线程带
        // 截止时间地收，避免「读一次刚好没读到」的假失败，也避免读不到时永久阻塞。
        let mut reader = session.reader().unwrap();
        let (tx, rx) = std::sync::mpsc::channel();
        std::thread::spawn(move || {
            let mut buf = [0u8; 4096];
            loop {
                match reader.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        if tx.send(String::from_utf8_lossy(&buf[..n]).into_owned()).is_err() {
                            break;
                        }
                    }
                    Err(_) => break,
                }
            }
        });

        // 让子进程回显环境变量，确认注入生效。回显的命令行里只有变量名、没有端口号，
        // 因此不会与展开结果混淆成假通过。
        session.write(b"echo $HTTPS_PROXY\n").unwrap();

        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(3);
        let mut out = String::new();
        while let Some(remaining) = deadline.checked_duration_since(std::time::Instant::now()) {
            match rx.recv_timeout(remaining) {
                Ok(chunk) => {
                    out.push_str(&chunk);
                    if out.contains("127.0.0.1:27890") {
                        break;
                    }
                }
                Err(_) => break,
            }
        }

        assert!(out.contains("127.0.0.1:27890"), "实际输出：{out}");
    }
}
