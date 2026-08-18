package com.mintpop.server.config;

import com.mintpop.server.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LinkPropertiesTest extends MysqlTestBase {

    @Autowired
    private LinkProperties linkProperties;

    @Test
    @DisplayName("链路有效期能从配置读入；用户与节点数据不再来自配置文件")
    void 链路有效期能从配置读入() {
        // 断言值（900）刻意不同于 LinkProperties.ttlSeconds 的默认值（1800）：
        // 若 @ConfigurationProperties 绑定被删掉，字段会回落到默认值，
        // 这条断言就会变红，而不是像之前那样即使绑定失效也照样通过
        assertThat(linkProperties.getTtlSeconds()).isEqualTo(900);
    }
}
