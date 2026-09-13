package ai.mintpop.lane.client;

import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 飞书群自定义机器人客户端：只管「签名 + POST 卡片 + 校验响应 code」，不懂业务。
 * 响应 code 非 0 抛 IllegalStateException；HTTP/网络层错误（超时、非 2xx）按 RestClient 原生异常上抛——
 * 调用方须整体 catch Exception 消化记日志。
 */
@Component
public class FeishuBotClient {

    private final RestClient restClient;
    private final NotifyProperties properties;

    public FeishuBotClient(RestClient feishuRestClient, NotifyProperties properties) {
        this.restClient = feishuRestClient;
        this.properties = properties;
    }

    /** 发送卡片；fields 为有序的「标签 → 值」，逐行渲染为 lark_md。标题色按事件性质选（见 FeishuCardTemplate） */
    public void sendCard(FeishuCardTemplate template, String title, LinkedHashMap<String, String> fields) {
        String content = fields.entrySet().stream()
                .map(e -> "**" + e.getKey() + "：**" + e.getValue())
                .collect(Collectors.joining("\n"));
        Map<String, Object> card = Map.of(
                "config", Map.of("wide_screen_mode", true),
                "header", Map.of(
                        "template", template.feishuName(),
                        "title", Map.of("tag", "plain_text", "content", title)),
                "elements", List.of(Map.of(
                        "tag", "div",
                        "text", Map.of("tag", "lark_md", "content", content))));
        // 飞书要求 timestamp/sign 与 msg_type 同级（JSON 键序无语义，LinkedHashMap 仅为可读性）
        Map<String, Object> body = new LinkedHashMap<>();
        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            long timestamp = Instant.now().getEpochSecond();
            body.put("timestamp", String.valueOf(timestamp));
            body.put("sign", sign(timestamp, properties.getSecret()));
        }
        body.put("msg_type", "interactive");
        body.put("card", card);

        Map<?, ?> resp = restClient.post()
                .uri(properties.getWebhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (resp == null || !(resp.get("code") instanceof Number code) || code.intValue() != 0) {
            throw new IllegalStateException("飞书机器人返回异常：" + resp);
        }
    }

    /** 飞书签名规范：以 timestamp+"\n"+secret 为 HMAC-SHA256 密钥、对空串取摘要后 Base64 */
    static String sign(long timestampSeconds, String secret) {
        try {
            String stringToSign = timestampSeconds + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("飞书签名计算失败", e);
        }
    }
}
