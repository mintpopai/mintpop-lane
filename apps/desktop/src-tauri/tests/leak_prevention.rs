//! 安全不变量测试：链路不可用时，流量必须断开而不是回落直连。
//! 需要 MIHOMO_BIN 指定 mihomo 二进制；未设置时跳过。

use std::path::PathBuf;
use std::time::Duration;

use app_lib::link::inbound::allocate_inbound;
use app_lib::link::kernel::MihomoKernel;

fn mihomo_bin() -> Option<PathBuf> {
    std::env::var("MIHOMO_BIN").ok().map(PathBuf::from)
}

/// 经指定的本地代理请求外网，返回是否成功
async fn can_reach_internet(proxy_url: &str) -> bool {
    let Ok(proxy) = reqwest::Proxy::all(proxy_url) else {
        return false;
    };
    let Ok(client) = reqwest::Client::builder()
        .proxy(proxy)
        .timeout(Duration::from_secs(10))
        .build()
    else {
        return false;
    };
    client
        .get("https://api.ipify.org")
        .send()
        .await
        .map(|r| r.status().is_success())
        .unwrap_or(false)
}

#[tokio::test]
async fn 空壳内核状态下流量必须断开而不是直连() {
    let Some(bin) = mihomo_bin() else {
        eprintln!("跳过：未设置 MIHOMO_BIN");
        return;
    };
    let dir = tempfile::tempdir().unwrap();
    let inbound = allocate_inbound().unwrap();

    let kernel = MihomoKernel::spawn(&bin, dir.path()).unwrap();
    kernel.wait_ready(Duration::from_secs(10)).await.unwrap();
    // 不推任何链路配置，内核停留在空壳状态

    assert!(
        !can_reach_internet(&inbound.proxy_url()).await,
        "空壳状态下竟然连通了外网——回落直连，安全不变量被破坏"
    );
}

#[tokio::test]
async fn 链路被重置后流量当场断开() {
    let Some(bin) = mihomo_bin() else {
        eprintln!("跳过：未设置 MIHOMO_BIN");
        return;
    };
    let Ok(fixture) = std::env::var("MINTPOP_TEST_LINK") else {
        eprintln!("跳过：未设置 MINTPOP_TEST_LINK（指向真实链路配置的 JSON）");
        return;
    };

    let dir = tempfile::tempdir().unwrap();
    let inbound = allocate_inbound().unwrap();
    let link: app_lib::link::model::LinkConfig =
        serde_json::from_str(&std::fs::read_to_string(fixture).unwrap()).unwrap();

    let kernel = MihomoKernel::spawn(&bin, dir.path()).unwrap();
    kernel.wait_ready(Duration::from_secs(10)).await.unwrap();

    let yaml = app_lib::link::render::render_kernel_config(&link, &inbound).unwrap();
    kernel.push_config(&yaml).await.unwrap();
    assert!(
        can_reach_internet(&inbound.proxy_url()).await,
        "推送链路配置后应当能连通外网"
    );

    // 模拟服务端吊销
    kernel.reset_to_empty().await.unwrap();
    assert!(
        !can_reach_internet(&inbound.proxy_url()).await,
        "吊销后竟然仍能连通外网——安全不变量被破坏"
    );
}
