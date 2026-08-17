package com.mintpop.server.service;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.entity.Employee;
import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.enumeration.EmployeeStatus;
import com.mintpop.server.exception.BizException;
import com.mintpop.server.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkServiceImplTest {

    private static Map<String, Object> node(String type, String server) {
        return Map.of("type", type, "server", server);
    }

    private static Employee employee(String subject, EmployeeStatus status) {
        Employee e = new Employee();
        e.setSubject(subject);
        e.setStatus(status);
        e.setExpectedEgressIps(List.of("77.47.143.6"));
        e.setLand(node("socks5", "77.47.143.6"));
        e.setClaudeCredential("sk-ant-test");
        return e;
    }

    /** 用假仓储替换真实实现，验证 DI 边界确实可替换 */
    private static LinkServiceImpl serviceWith(Employee employee) {
        LinkProperties props = new LinkProperties();
        props.setFront(node("trojan", "us.test.example"));
        props.setTtlSeconds(1800);

        EmployeeRepository repo = subject ->
                employee != null && employee.getSubject().equals(subject)
                        ? Optional.of(employee)
                        : Optional.empty();

        return new LinkServiceImpl(props, repo);
    }

    @Test
    @DisplayName("正常员工能拿到完整链路与席位凭据")
    void 正常员工能拿到完整链路() {
        var service = serviceWith(employee("u1", EmployeeStatus.ACTIVE));
        var resp = service.resolveLink("u1");

        assertThat(resp.front()).containsEntry("type", "trojan");
        assertThat(resp.land()).containsEntry("server", "77.47.143.6");
        assertThat(resp.expectedEgressIps()).containsExactly("77.47.143.6");
        assertThat(resp.claudeCredential()).isEqualTo("sk-ant-test");
        assertThat(resp.ttlSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("未录入绑定表的账号被拒绝")
    void 未录入绑定表的账号被拒绝() {
        var service = serviceWith(null);

        assertThatThrownBy(() -> service.resolveLink("陌生人"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ACCOUNT_NOT_ENROLLED);
    }

    @Test
    @DisplayName("已吊销的员工拿不到链路")
    void 已吊销的员工拿不到链路() {
        var service = serviceWith(employee("u1", EmployeeStatus.REVOKED));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("缺少出口 IP 的绑定被拒绝，避免客户端放弃出口校验")
    void 缺少出口ip的绑定被拒绝() {
        Employee broken = employee("u1", EmployeeStatus.ACTIVE);
        broken.setExpectedEgressIps(List.of());
        var service = serviceWith(broken);

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("缺少席位凭据的绑定被拒绝")
    void 缺少席位凭据的绑定被拒绝() {
        Employee broken = employee("u1", EmployeeStatus.ACTIVE);
        broken.setClaudeCredential("  ");
        var service = serviceWith(broken);

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("心跳如实返回员工状态")
    void 心跳如实返回员工状态() {
        assertThat(serviceWith(employee("u1", EmployeeStatus.ACTIVE)).heartbeat("u1").status())
                .isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(serviceWith(employee("u1", EmployeeStatus.REVOKED)).heartbeat("u1").status())
                .isEqualTo(EmployeeStatus.REVOKED);
    }

    @Test
    @DisplayName("未录入账号的心跳按吊销处理，客户端据此断链")
    void 未录入账号的心跳按吊销处理() {
        // 从绑定表里被删掉等价于被收回权限，必须让客户端断链而不是继续用
        assertThat(serviceWith(null).heartbeat("陌生人").status())
                .isEqualTo(EmployeeStatus.REVOKED);
    }
}
