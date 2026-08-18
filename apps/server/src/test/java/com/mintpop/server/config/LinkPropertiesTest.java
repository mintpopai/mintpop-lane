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
        assertThat(linkProperties.getTtlSeconds()).isEqualTo(1800);
    }
}
