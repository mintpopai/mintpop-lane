package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Claude OAuth 端点与常量。取自 Claude Code CLI 2.1.246 的 prod 配置块。
 *
 * scope 是最小充分集，不可增删：多要 user:mcp_servers 会被服务端拒发长有效期
 * （实测 400 Custom expires_in not allowed for scope 'user:mcp_servers'），
 * 少要 user:profile 则整个方案的目的落空。
 */
@Data
@Component
@ConfigurationProperties(prefix = "claude.oauth")
public class ClaudeOAuthProperties {

    private String clientId = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";

    private String authorizeUrl = "https://claude.com/cai/oauth/authorize";

    private String tokenUrl = "https://platform.claude.com/v1/oauth/token";

    private String revokeUrl = "https://platform.claude.com/v1/oauth/token/revoke";

    private String redirectUri = "https://platform.claude.com/oauth/code/callback";

    /** 最小充分集，不可增删，见类注释 */
    private String scope = "user:profile user:inference";

    /** 兑换凭证时请求的有效期上限（秒） */
    private long maxExpiresInSeconds = 31536000L;
}
