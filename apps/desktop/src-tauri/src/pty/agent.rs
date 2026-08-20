/// agent 类型 → CLI 命令与凭据环境变量的映射表。
/// 这是客户端对「支持哪些 agent」的唯一权威定义；
/// 服务端下发了这里不认识的类型时，使用侧直接忽略（向前兼容）。
pub struct AgentSpec {
    /// 与服务端 AgentType 枚举逐字一致的类型名
    pub agent_type: &'static str,
    /// 会话向导里展示给用户的名字
    pub display_name: &'static str,
    /// 在 workspace 里直接运行的 CLI 命令（经 PATH 解析）
    pub command: &'static str,
    /// 凭据注入的环境变量名
    pub credential_env: &'static str,
}

pub const AGENTS: &[AgentSpec] = &[
    AgentSpec {
        agent_type: "CLAUDE",
        display_name: "Claude Code",
        command: "claude",
        credential_env: "CLAUDE_CODE_OAUTH_TOKEN",
    },
    AgentSpec {
        agent_type: "CODEX",
        display_name: "Codex",
        command: "codex",
        credential_env: "OPENAI_API_KEY",
    },
];

pub fn spec_of(agent_type: &str) -> Option<&'static AgentSpec> {
    AGENTS.iter().find(|a| a.agent_type == agent_type)
}

#[cfg(test)]
// 测试名嵌入 None 等 ASCII 词以贴合场景描述，clippy 的 snake_case 检查按 ASCII 字母判定，
// 与中文测试名混排时会误判，此处放行（与 link/state.rs 的既有做法一致）
#[allow(non_snake_case)]
mod tests {
    use super::*;

    #[test]
    fn 已知agent类型能查到映射() {
        let claude = spec_of("CLAUDE").unwrap();
        assert_eq!(claude.command, "claude");
        assert_eq!(claude.credential_env, "CLAUDE_CODE_OAUTH_TOKEN");

        let codex = spec_of("CODEX").unwrap();
        assert_eq!(codex.command, "codex");
        assert_eq!(codex.credential_env, "OPENAI_API_KEY");
    }

    #[test]
    fn 未知agent类型返回None() {
        assert!(spec_of("FUTURE_AGENT").is_none());
    }
}
