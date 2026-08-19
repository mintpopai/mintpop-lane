//! 内核生命周期集成测试。
//! 需要真实的 mihomo 二进制，通过环境变量 MIHOMO_BIN 指定；未设置时跳过。

use std::path::PathBuf;
use std::time::Duration;

use app_lib::link::kernel::MihomoKernel;

fn mihomo_bin() -> Option<PathBuf> {
    std::env::var("MIHOMO_BIN").ok().map(PathBuf::from)
}

#[tokio::test]
async fn 内核以空壳配置启动后可响应控制接口() {
    let Some(bin) = mihomo_bin() else {
        eprintln!("跳过：未设置 MIHOMO_BIN");
        return;
    };
    let dir = tempfile::tempdir().unwrap();

    let kernel = MihomoKernel::spawn(&bin, dir.path()).unwrap();
    kernel.wait_ready(Duration::from_secs(10)).await.unwrap();
}

#[tokio::test]
async fn 热加载配置后不会把凭据写回磁盘() {
    let Some(bin) = mihomo_bin() else {
        eprintln!("跳过：未设置 MIHOMO_BIN");
        return;
    };
    let dir = tempfile::tempdir().unwrap();

    let kernel = MihomoKernel::spawn(&bin, dir.path()).unwrap();
    kernel.wait_ready(Duration::from_secs(10)).await.unwrap();

    let secret_marker = "PIER_SECRET_MARKER_9F2A";
    let yaml = format!(
        "allow-lan: false\nmode: rule\nproxies:\n  - name: LAND\n    type: socks5\n    \
         server: 127.0.0.1\n    port: 1080\n    password: {secret_marker}\nrules:\n  - MATCH,REJECT\n"
    );
    kernel.push_config(&yaml).await.unwrap();

    // 遍历工作目录，确认凭据没有被内核写回任何文件（风险清单 #3）
    for entry in std::fs::read_dir(dir.path()).unwrap() {
        let path = entry.unwrap().path();
        if path.is_file() {
            let content = std::fs::read_to_string(&path).unwrap_or_default();
            assert!(
                !content.contains(secret_marker),
                "凭据被写入了 {}",
                path.display()
            );
        }
    }
}
