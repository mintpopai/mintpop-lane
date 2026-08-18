package com.mintpop.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 插件装配。分页是管理端列表接口的前提。 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 经典 Jackson 2 的 ObjectMapper。
     * Spring Boot 4.1 默认只自动装配 Jackson 3（tools.jackson）的 JsonMapper，
     * 而 MyBatis-Plus 的 JacksonTypeHandler 与 converter 层的敏感键序列化
     * 都依赖这个经典类型，故自行提供一个 bean。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
