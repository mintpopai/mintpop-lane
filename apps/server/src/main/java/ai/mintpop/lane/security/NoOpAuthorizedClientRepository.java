package ai.mintpop.lane.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 丢弃 Logto 的 authorized client（access/refresh token）：本系统只把 OIDC 用于一次性握手，
 * 登录成功后 token 用完即弃，不下发、不落库，也不允许框架把它悄悄存进 HttpSession
 * （默认实现 {@code HttpSessionOAuth2AuthorizedClientRepository} 会话即状态，与
 * 「后端保持无状态、只认自签会话 token」相悖，顺带还会下发 JSESSIONID）。
 * save/remove 空实现，load 恒返回 null——效果等同「从不持有」。
 */
public class NoOpAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        // 有意不存：token 用完即弃
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                       HttpServletRequest request, HttpServletResponse response) {
        // 从未持有，无需移除
    }
}
