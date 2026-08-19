use crate::link::model::InboundCredentials;
use crate::link::state::LinkState;
use crate::pty::env::build_agent_env;
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
}

/// 当前平台的默认 shell
pub fn default_shell() -> &'static str {
    if cfg!(windows) {
        "powershell.exe"
    } else {
        "/bin/zsh"
    }
}

pub struct PtySession {
    master: Box<dyn MasterPty + Send>,
    writer: Mutex<Box<dyn Write + Send>>,
    _child: Box<dyn portable_pty::Child + Send + Sync>,
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
}

/// 启动 Agent 子进程的唯一入口。
/// 任何子进程都必须经过这里，注入在此发生，前端无法绕过。
pub fn spawn_agent_pty(
    state: LinkState,
    inbound: &InboundCredentials,
    claude_credential: &str,
    shell: &str,
    rows: u16,
    cols: u16,
) -> Result<PtySession, SpawnError> {
    // 第一道守卫：链路不活跃就直接拒绝，绝不以直连方式放行
    if !state.allows_spawn() {
        return Err(SpawnError::LINK_NOT_ACTIVE(state));
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

    let mut cmd = CommandBuilder::new(shell);
    for (key, value) in build_agent_env(inbound, claude_credential) {
        cmd.env(key, value);
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
        _child: child,
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
            let result = spawn_agent_pty(state, &sample_inbound(), "tok", "/bin/sh", 24, 80);
            match result {
                Err(SpawnError::LINK_NOT_ACTIVE(s)) => assert_eq!(s, state),
                _ => panic!("状态 {state:?} 下不应放行"),
            }
        }
    }

    #[test]
    fn 活跃状态下可以启动子进程且注入了代理变量() {
        // 用 /bin/sh 而非 default_shell()：本用例验的是「注入的环境变量有没有传进子进程」，
        // 这与用哪个 shell 无关。交互式 zsh 会读用户的 ~/.zshrc，既会往 PTY 里吐提示符与
        // 终端标题转义序列干扰断言，也会让结果取决于每个人的 dotfiles。
        let session = spawn_agent_pty(
            LinkState::ACTIVE,
            &sample_inbound(),
            "tok",
            "/bin/sh",
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
