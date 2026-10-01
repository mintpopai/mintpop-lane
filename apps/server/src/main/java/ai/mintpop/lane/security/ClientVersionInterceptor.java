package ai.mintpop.lane.security;

import ai.mintpop.lane.service.ClientVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 桌面端接口的版本闸门：落后于最新版的客户端在进 controller 之前就被拦下。
 * <p>
 * 用拦截器而不是过滤器：抛出的 BizException 走 DispatcherServlet 的异常解析，
 * 由全局异常处理器收口成 HTTP 200 + 业务码，客户端据业务码进入强制更新。
 * 过滤器里抛出去的异常绕过全局处理器，只会变成一个 500，客户端认不出来。
 * 挂在哪些路径见 ClientVersionConfig。
 */
public class ClientVersionInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Client-Version";

    private final ClientVersionService clientVersionService;

    public ClientVersionInterceptor(ClientVersionService clientVersionService) {
        this.clientVersionService = clientVersionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        clientVersionService.requireCurrent(request.getHeader(HEADER));
        return true;
    }
}
