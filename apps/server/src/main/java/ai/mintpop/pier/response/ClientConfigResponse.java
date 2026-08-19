package ai.mintpop.pier.response;

/**
 * 下发给桌面端的登录接入配置。字段名与客户端 ClientConfigData 逐字对应。
 * 三个值均为公开信息，故该端点匿名可访问。
 */
public record ClientConfigResponse(
        String logtoIssuer,
        String logtoClientId,
        String apiResource
) {
}
