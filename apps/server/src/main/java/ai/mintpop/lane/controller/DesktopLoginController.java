package ai.mintpop.lane.controller;

import ai.mintpop.lane.security.DesktopFlowCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 桌面端登录入口：暂存 PKCE challenge 与 state 后，把浏览器送进 Spring 管理的 OIDC 握手。
 * 非 REST 接口（纯跳转），不走 ApiResponse。
 */
@Controller
public class DesktopLoginController {

    /** RFC 7636：S256 的 challenge 是 43 位 base64url 字符 */
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    /** state 为桌面端生成的随机串：只放行 URL 安全字符，防开放跳转参数注入 */
    private static final Pattern STATE = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final DesktopFlowCookie desktopFlowCookie;

    public DesktopLoginController(DesktopFlowCookie desktopFlowCookie) {
        this.desktopFlowCookie = desktopFlowCookie;
    }

    @GetMapping("/auth/desktop/start")
    public void start(@RequestParam("code_challenge") String codeChallenge,
                      @RequestParam("state") String state,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!CHALLENGE.matcher(codeChallenge).matches() || !STATE.matcher(state).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "非法的登录参数");
            return;
        }
        desktopFlowCookie.write(request, response, codeChallenge, state);
        response.sendRedirect("/oauth2/authorization/logto");
    }
}
