package ai.mintpop.lane.response;

/** 桌面端兑换到的会话。token 存 OS 钥匙串，expiresInSeconds 供客户端提前感知过期。 */
public record DesktopSessionResponse(String token, long expiresInSeconds) {
}
