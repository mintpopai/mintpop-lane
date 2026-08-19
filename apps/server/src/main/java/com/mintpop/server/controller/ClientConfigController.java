package com.mintpop.server.controller;

import com.mintpop.server.config.ClientProperties;
import com.mintpop.server.response.ApiResponse;
import com.mintpop.server.response.ClientConfigResponse;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端引导配置：回答「怎么登录」。
 * 与 /api/link/config（回答「登录后给你什么」）分工明确——前者匿名，后者必须带 JWT。
 * 这里下发的三个值都是公开信息（公共客户端的 client_id 本就会出现在浏览器地址栏），
 * 真正的秘密只经 /api/link/config 下发。
 */
@RestController
public class ClientConfigController {

    private final ClientProperties clientProperties;
    private final OAuth2ResourceServerProperties resourceServerProperties;

    public ClientConfigController(ClientProperties clientProperties,
                                  OAuth2ResourceServerProperties resourceServerProperties) {
        this.clientProperties = clientProperties;
        this.resourceServerProperties = resourceServerProperties;
        // 缺失的鉴权配置是部署错误，必须在启动时就炸出来，而不是让每台客户端各自
        // 报一遍"网络请求失败"——issuer/audience 缺失时下发给客户端的是结构合法但
        // 不可用的空值，客户端解析必然失败，却把运维指向了错误的排查方向。
        OAuth2ResourceServerProperties.Jwt jwt = resourceServerProperties.getJwt();
        if (jwt.getIssuerUri() == null || jwt.getIssuerUri().isBlank()) {
            throw new IllegalStateException(
                    "缺少配置 spring.security.oauth2.resourceserver.jwt.issuer-uri");
        }
        if (jwt.getAudiences() == null || jwt.getAudiences().isEmpty()) {
            throw new IllegalStateException(
                    "缺少配置 spring.security.oauth2.resourceserver.jwt.audiences");
        }
    }

    @GetMapping("/api/client-config")
    public ApiResponse<ClientConfigResponse> clientConfig() {
        OAuth2ResourceServerProperties.Jwt jwt = resourceServerProperties.getJwt();
        // 客户端拿它当授权请求的 resource 参数，服务端用同一个值校验 aud，一份配置派生两处；
        // 构造器已断言 audiences 非空，这里不必再判空
        String apiResource = jwt.getAudiences().get(0);

        return ApiResponse.success(new ClientConfigResponse(
                jwt.getIssuerUri(),
                clientProperties.getLogtoClientId(),
                apiResource));
    }
}
