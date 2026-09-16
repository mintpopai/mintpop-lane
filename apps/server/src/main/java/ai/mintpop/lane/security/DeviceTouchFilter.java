package ai.mintpop.lane.security;

import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.util.DeviceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 顺带记录设备的最近上报时刻，供管理端回答「这台机器还在不在用」。
 *
 * <p>为什么收在过滤器而不是各接口里：控制面请求有五处（拉链路配置、心跳、/api/me、登录兑换、
 * 绑定与申请），客户端在它们身上统一挂了 X-Device-Id（见桌面端 link/remote.rs 的默认请求头）。
 * 逐处去写活跃时刻迟早漏一处，而漏掉的那处恰恰是最能说明「还在用」的心跳——它连
 * X-Device-Id 都没读。挂在这里，以后新增的接口也自动算数。
 *
 * <p>这里是旁路，不是校验点：拿不到用户、没带头、头的形状不对，一律跳过而不是拒绝请求。
 * 管理端网页本来就不带这个头，把缺头当错误会把整个管理端打挂；真正的校验仍在各接口的
 * {@link DeviceId#normalize} 那里。同理，刷新失败只记日志——活跃时刻记不上是小事，
 * 为它把一个正常的业务请求打回去才是大事。
 *
 * <p>注意：不加 @Component——与 SessionAuthFilter 同理，由 SecurityConfig 显式装入安全链，
 * 避免被 Servlet 容器再注册一次。
 */
@Slf4j
public class DeviceTouchFilter extends OncePerRequestFilter {

    /**
     * 节流窗口。活跃设备每 60 秒一次心跳，无条件写会让这张表变成高频写入点；
     * 而「这台机器还在不在用」这个判断，差五分钟和差一秒没有任何区别。
     */
    private static final Duration THROTTLE = Duration.ofMinutes(5);

    private final UserDeviceRepository userDeviceRepository;
    private final Clock clock;

    public DeviceTouchFilter(UserDeviceRepository userDeviceRepository, Clock clock) {
        this.userDeviceRepository = userDeviceRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        touch(request);
        filterChain.doFilter(request, response);
    }

    private void touch(HttpServletRequest request) {
        Long userId = authenticatedUserId();
        if (userId == null) {
            return;
        }
        DeviceId.tryNormalize(request.getHeader("X-Device-Id")).ifPresent(deviceId -> {
            try {
                Instant now = clock.instant();
                userDeviceRepository.touchLastSeen(userId, deviceId, now, now.minus(THROTTLE));
            } catch (RuntimeException e) {
                log.warn("刷新设备最近上报时刻失败，userId={}", userId, e);
            }
        });
    }

    /** 匿名认证的 principal 是字符串 "anonymousUser"，据类型判定即可区分，不必另判认证类型 */
    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }
}
