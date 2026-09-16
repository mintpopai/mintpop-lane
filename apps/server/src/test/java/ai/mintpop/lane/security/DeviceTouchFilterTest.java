package ai.mintpop.lane.security;

import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.repository.UserDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTouchFilterTest {

    private static final String DEVICE_ID = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    private RecordingDeviceRepository repository;
    private DeviceTouchFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        repository = new RecordingDeviceRepository();
        filter = new DeviceTouchFilter(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("已认证且带机器码：按节流窗口刷新该设备的最近上报时刻")
    void touchesDeviceOfAuthenticatedRequest() throws Exception {
        authenticateAs(42L);
        request.addHeader("X-Device-Id", DEVICE_ID.toUpperCase());

        filter.doFilter(request, response, chain);

        assertThat(repository.calls).hasSize(1);
        TouchCall call = repository.calls.getFirst();
        assertThat(call.userId()).isEqualTo(42L);
        // 大小写归一化后才落库，否则同一台机器会被当成两台
        assertThat(call.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(call.now()).isEqualTo(NOW);
        assertThat(call.staleBefore()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("管理端网页不带机器码：什么也不做")
    void skipsRequestWithoutDeviceHeader() throws Exception {
        authenticateAs(42L);

        filter.doFilter(request, response, chain);

        assertThat(repository.calls).isEmpty();
    }

    @Test
    @DisplayName("机器码形状不对：跳过而不是拒绝请求——这里是旁路，校验归各接口自己")
    void skipsMalformedDeviceHeader() throws Exception {
        authenticateAs(42L);
        request.addHeader("X-Device-Id", "not-a-sha256");

        filter.doFilter(request, response, chain);

        assertThat(repository.calls).isEmpty();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("未认证请求：拿不到用户就无从判断设备属于谁，跳过")
    void skipsUnauthenticatedRequest() throws Exception {
        request.addHeader("X-Device-Id", DEVICE_ID);

        filter.doFilter(request, response, chain);

        assertThat(repository.calls).isEmpty();
    }

    @Test
    @DisplayName("匿名认证请求：principal 不是用户 id，同样跳过")
    void skipsAnonymousRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        request.addHeader("X-Device-Id", DEVICE_ID);

        filter.doFilter(request, response, chain);

        assertThat(repository.calls).isEmpty();
    }

    @Test
    @DisplayName("刷新失败不连累业务请求：仓储抛异常时请求照常往下走")
    void repositoryFailureDoesNotBreakRequest() throws Exception {
        authenticateAs(42L);
        request.addHeader("X-Device-Id", DEVICE_ID);
        repository.failure = new IllegalStateException("数据库连不上");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("无论是否刷新，请求都要继续往下走")
    void alwaysContinuesTheChain() throws Exception {
        authenticateAs(42L);
        request.addHeader("X-Device-Id", DEVICE_ID);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private record TouchCall(Long userId, String deviceId, Instant now, Instant staleBefore) {
    }

    /** 记录调用的设备仓储替身：断言的是 filter 让仓储做了什么，其余方法本测试用不到 */
    private static final class RecordingDeviceRepository implements UserDeviceRepository {

        private final List<TouchCall> calls = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public boolean touchLastSeen(Long userId, String deviceId, Instant now, Instant staleBefore) {
            calls.add(new TouchCall(userId, deviceId, now, staleBefore));
            if (failure != null) {
                throw failure;
            }
            return true;
        }

        @Override
        public UserDevice upsert(Long userId, String deviceId, String name, String os, String model, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserDevice> findByUserId(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<UserDevice> findById(Long id) {
            throw new UnsupportedOperationException();
        }
    }
}
