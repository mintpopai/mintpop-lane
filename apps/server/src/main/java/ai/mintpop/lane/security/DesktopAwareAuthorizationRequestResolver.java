package ai.mintpop.lane.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 桌面流的 OIDC 授权请求追加 prompt=login，强制 Logto 重新认证。
 * 桌面端登出只清本地钥匙串，浏览器里 IdP 会话仍在；不强制的话重新登录会被静默放行，
 * 用户见不到用户名密码页，登出即成错觉、也无法换账号。是否桌面流以握手中间态
 * Cookie（{@link DesktopFlowCookie}）为准；网页流不带该 Cookie，保留 SSO 便利。
 */
public class DesktopAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final DesktopFlowCookie desktopFlowCookie;

    public DesktopAwareAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                    DesktopFlowCookie desktopFlowCookie) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.desktopFlowCookie = desktopFlowCookie;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return forceLoginIfDesktop(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return forceLoginIfDesktop(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest forceLoginIfDesktop(HttpServletRequest request,
                                                           OAuth2AuthorizationRequest resolved) {
        if (resolved == null || desktopFlowCookie.read(request).isEmpty()) {
            return resolved;
        }
        return OAuth2AuthorizationRequest.from(resolved)
                .additionalParameters(params -> params.put("prompt", "login"))
                .build();
    }
}
