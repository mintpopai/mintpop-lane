package com.mintpop.server.controller;

import com.mintpop.server.config.ClientProperties;
import com.mintpop.server.response.ApiResponse;
import com.mintpop.server.response.ClientConfigResponse;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    }

    @GetMapping("/api/client-config")
    public ApiResponse<ClientConfigResponse> clientConfig() {
        OAuth2ResourceServerProperties.Jwt jwt = resourceServerProperties.getJwt();
        List<String> audiences = jwt.getAudiences();
        // 客户端拿它当授权请求的 resource 参数，服务端用同一个值校验 aud，一份配置派生两处
        String apiResource = (audiences == null || audiences.isEmpty()) ? null : audiences.get(0);

        return ApiResponse.success(new ClientConfigResponse(
                jwt.getIssuerUri(),
                clientProperties.getLogtoClientId(),
                apiResource));
    }
}
