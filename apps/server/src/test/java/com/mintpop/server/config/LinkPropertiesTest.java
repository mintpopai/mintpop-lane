package com.mintpop.server.config;

import com.mintpop.server.enumeration.EmployeeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LinkPropertiesTest {

    @Autowired
    private LinkProperties linkProperties;

    @Test
    @DisplayName("第一跳节点的原始属性被完整读入")
    void 第一跳节点的原始属性被完整读入() {
        assertThat(linkProperties.getFront())
                .containsEntry("type", "trojan")
                .containsEntry("server", "us.test.example");
    }

    @Test
    @DisplayName("员工绑定表按人读入，含出口 IP 与席位凭据")
    void 员工绑定表按人读入() {
        assertThat(linkProperties.getEmployees()).hasSize(2);

        var first = linkProperties.getEmployees().getFirst();
        assertThat(first.getSubject()).isEqualTo("logto-user-1");
        assertThat(first.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(first.getExpectedEgressIps()).containsExactly("77.47.143.6");
        assertThat(first.getClaudeCredential()).isEqualTo("sk-ant-test-1");
        assertThat(first.getLand()).containsEntry("type", "socks5");
    }

    @Test
    @DisplayName("被停用的员工同样能读入，状态如实反映")
    void 被停用的员工状态如实反映() {
        var second = linkProperties.getEmployees().get(1);
        assertThat(second.getStatus()).isEqualTo(EmployeeStatus.REVOKED);
    }
}
