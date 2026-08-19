package ai.mintpop.lane.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 自签会话配置（lane.auth.*）。
 * 密钥与 lane.crypto.key 同级管理：本地在 application-local.yaml，生产由 compose 从 .env 注入。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "lane.auth")
public class AuthProperties {

    /** 管理端网页的会话 Cookie 名 */
    public static final String SESSION_COOKIE_NAME = "lane_session";

    /** 桌面端登录握手中间态（PKCE challenge + state）的 Cookie 名 */
    public static final String DESKTOP_FLOW_COOKIE_NAME = "lane_desktop_flow";

    /** 自签会话 JWT 的 HS256 密钥，至少 32 字节（openssl rand -base64 32 生成） */
    @NotBlank
    private String sessionSecret;

    /** 管理端网页会话有效期 */
    private Duration webSessionTtl = Duration.ofDays(7);

    /** 桌面端会话有效期（存 OS 钥匙串） */
    private Duration desktopSessionTtl = Duration.ofDays(30);

    /** 管理端前端地址：网页登录成功/失败后的回跳落点 */
    @NotBlank
    private String adminFrontendUrl;
}
