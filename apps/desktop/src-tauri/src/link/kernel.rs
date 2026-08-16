use crate::link::inbound::allocate_free_port;
use crate::link::render::render_bootstrap_config;
use std::path::Path;
use std::process::{Child, Command};
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum KernelError {
    #[error("启动内核进程失败：{0}")]
    Spawn(#[source] std::io::Error),
    #[error("写入引导配置失败：{0}")]
    WriteBootstrap(#[source] std::io::Error),
    #[error("分配控制接口端口失败：{0}")]
    Port(#[from] crate::link::inbound::InboundError),
    #[error("内核在超时时间内未就绪")]
    NotReady,
    #[error("控制接口请求失败：{0}")]
    Api(#[source] reqwest::Error),
    #[error("控制接口返回错误状态：{0}")]
    ApiStatus(reqwest::StatusCode),
}

/// 内核在空壳状态下的配置：不含任何节点，兜底拒绝一切
const EMPTY_CONFIG: &str = "allow-lan: false\nmode: rule\nrules:\n  - MATCH,REJECT\n";

pub struct MihomoKernel {
    child: Child,
    controller_port: u16,
    secret: String,
    http: reqwest::Client,
}

impl MihomoKernel {
    /// 以空壳引导配置启动内核。真实链路随后经 push_config 热加载，凭据因此不落盘。
    pub fn spawn(binary: &Path, work_dir: &Path) -> Result<Self, KernelError> {
        let controller_port = allocate_free_port()?;
        let secret = crate::link::inbound::allocate_inbound()?.password;

        let bootstrap = render_bootstrap_config(controller_port, &secret);
        let config_path = work_dir.join("bootstrap.yaml");
        std::fs::write(&config_path, bootstrap).map_err(KernelError::WriteBootstrap)?;
        restrict_permissions(&config_path).map_err(KernelError::WriteBootstrap)?;

        let child = Command::new(binary)
            .arg("-d")
            .arg(work_dir)
            .arg("-f")
            .arg(&config_path)
            .spawn()
            .map_err(KernelError::Spawn)?;

        Ok(Self {
            child,
            controller_port,
            secret,
            // 控制接口在回环上，不要让系统代理设置干扰它
            http: reqwest::Client::builder()
                .no_proxy()
                .build()
                .expect("构建 HTTP 客户端不应失败"),
        })
    }

    fn api(&self, path: &str) -> String {
        format!("http://127.0.0.1:{}{}", self.controller_port, path)
    }

    /// 轮询控制接口直到内核可响应
    pub async fn wait_ready(&self, timeout: Duration) -> Result<(), KernelError> {
        let deadline = std::time::Instant::now() + timeout;
        while std::time::Instant::now() < deadline {
            let ok = self
                .http
                .get(self.api("/version"))
                .bearer_auth(&self.secret)
                .send()
                .await
                .map(|r| r.status().is_success())
                .unwrap_or(false);
            if ok {
                return Ok(());
            }
            tokio::time::sleep(Duration::from_millis(200)).await;
        }
        Err(KernelError::NotReady)
    }

    /// 以 payload 形式热加载配置，全程不经过磁盘
    pub async fn push_config(&self, yaml: &str) -> Result<(), KernelError> {
        let resp = self
            .http
            .put(self.api("/configs?force=true"))
            .bearer_auth(&self.secret)
            .json(&serde_json::json!({ "path": "", "payload": yaml }))
            .send()
            .await
            .map_err(KernelError::Api)?;

        if resp.status().is_success() {
            Ok(())
        } else {
            Err(KernelError::ApiStatus(resp.status()))
        }
    }

    /// 把内核打回空壳状态：链路被吊销或降级时调用，流量当场断开
    pub async fn reset_to_empty(&self) -> Result<(), KernelError> {
        self.push_config(EMPTY_CONFIG).await
    }

    /// 内核进程是否仍存活
    pub fn is_alive(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }
}

impl Drop for MihomoKernel {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

#[cfg(unix)]
fn restrict_permissions(path: &Path) -> std::io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
}

#[cfg(not(unix))]
fn restrict_permissions(_path: &Path) -> std::io::Result<()> {
    // Windows 下配置写在 App 私有目录，依赖目录 ACL
    Ok(())
}
