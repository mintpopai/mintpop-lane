use serde::{Deserialize, Serialize};
use serde_yaml_ng::Mapping;

/// 第一跳节点在内核配置中的固定名称（美国机房机场节点，负责出国）
pub const FRONT_NAME: &str = "FRONT";
/// 第二跳节点在内核配置中的固定名称（后置落地代理，决定最终出口 IP）
pub const LAND_NAME: &str = "LAND";

/// 链路配置。第一期由本地静态文件提供，计划二改为服务端下发，形状保持一致。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LinkConfig {
    /// 第一跳节点，原样透传的 mihomo 节点配置（协议字段千变万化，不做强类型化）
    pub front: Mapping,
    /// 第二跳节点，原样透传的 mihomo 节点配置
    pub land: Mapping,
    /// 期望的落地出口 IP 集合，用于出口校验
    pub expected_egress_ips: Vec<String>,
    /// 该用户席位的 Claude 长效凭据
    pub claude_credential: String,
    /// 配置有效期（秒）
    pub ttl_seconds: u64,
}

/// 本地入站监听的端口与凭据，每次启动重新生成，不持久化
#[derive(Debug, Clone)]
pub struct InboundCredentials {
    pub port: u16,
    pub username: String,
    pub password: String,
}

impl InboundCredentials {
    /// 供子进程环境变量与探测请求使用的代理地址
    pub fn proxy_url(&self) -> String {
        format!(
            "http://{}:{}@127.0.0.1:{}",
            self.username, self.password, self.port
        )
    }
}
