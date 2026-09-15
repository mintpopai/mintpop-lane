package ai.mintpop.lane.client;

import ai.mintpop.lane.config.PaymentProperties;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.exception.BizException;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stripe SDK 唯一封装点：创建 / 检索 / 取消 PaymentIntent、webhook 验签。
 * 业务分支不在这层；出错记日志后转业务异常，服务层测试用 @MockitoBean 整个替换掉它。
 */
@Slf4j
@Component
public class StripeGateway {

    private final PaymentProperties properties;
    /** 懒初始化的客户端：配置未填时保持 null，用到才报未配置，不让缺配置阻塞启动 */
    private volatile StripeClient client;

    public StripeGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建 PaymentIntent。硬约定：幂等键 pi-<订单号>、metadata.orderId / product、微信必带 client=web。
     * 金额直接用订单的 amountMinor，币种按套餐币种。
     */
    public PaymentIntent createPaymentIntent(String orderNo, long amountMinor, Currency currency,
                                             String subject, List<String> paymentMethodTypes) {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountMinor)
                .setCurrency(currency.name().toLowerCase(Locale.ROOT))
                .setDescription(subject)
                .putMetadata("orderId", orderNo)
                .putMetadata("product", properties.getProductCode());
        String suffix = properties.getStatementDescriptorSuffix();
        if (paymentMethodTypes.contains("card") && suffix != null && !suffix.isBlank()) {
            builder.setStatementDescriptorSuffix(suffix);
        }
        paymentMethodTypes.forEach(builder::addPaymentMethodType);
        if (paymentMethodTypes.contains("wechat_pay")) {
            builder.setPaymentMethodOptions(PaymentIntentCreateParams.PaymentMethodOptions.builder()
                    .setWechatPay(PaymentIntentCreateParams.PaymentMethodOptions.WechatPay.builder()
                            .setClient(PaymentIntentCreateParams.PaymentMethodOptions.WechatPay.Client.WEB)
                            .build())
                    .build());
        }
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("pi-" + orderNo).build();
        try {
            return client().v1().paymentIntents().create(builder.build(), options);
        } catch (StripeException e) {
            log.error("创建 PaymentIntent 失败 orderNo={}", orderNo, e);
            throw new BizException(BizCodeEnum.PAYMENT_GATEWAY_ERROR);
        }
    }

    public PaymentIntent retrievePaymentIntent(String intentId) {
        try {
            return client().v1().paymentIntents().retrieve(intentId);
        } catch (StripeException e) {
            log.error("检索 PaymentIntent 失败 intentId={}", intentId, e);
            throw new BizException(BizCodeEnum.PAYMENT_GATEWAY_ERROR);
        }
    }

    /**
     * 尽力而为取消：订单取消 / 过期时撤掉 Stripe 侧凭据，令残留支付页失效。
     * 失败只记日志：竞态下该笔可能已 succeeded（不可取消），钱已收由 webhook 入账兜底。
     */
    public void cancelPaymentIntent(String intentId) {
        if (!properties.isConfigured()) {
            log.warn("支付未配置，跳过取消 PaymentIntent intentId={}", intentId);
            return;
        }
        try {
            client().v1().paymentIntents().cancel(intentId, PaymentIntentCancelParams.builder()
                    .setCancellationReason(PaymentIntentCancelParams.CancellationReason.ABANDONED)
                    .build());
        } catch (StripeException e) {
            log.warn("取消 PaymentIntent 失败（可能已支付，由 webhook 入账兜底）intentId={}", intentId, e);
        }
    }

    /** webhook 验签并解出业务字段。验签失败抛 SignatureVerificationException（控制器回 400） */
    public StripeWebhookEvent parseWebhookEvent(String payload, String signatureHeader)
            throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, signatureHeader, requiredWebhookSecret());
        PaymentIntent intent = null;
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        try {
            StripeObject object = deserializer.getObject().isPresent()
                    ? deserializer.getObject().get() : deserializer.deserializeUnsafe();
            if (object instanceof PaymentIntent pi) {
                intent = pi;
            }
        } catch (EventDataObjectDeserializationException e) {
            log.warn("webhook 事件对象反序列化失败 eventType={}", event.getType(), e);
        }
        if (intent == null) {
            return new StripeWebhookEvent(event.getType(), null, null, null, null, null);
        }
        Map<String, String> metadata = intent.getMetadata();
        return new StripeWebhookEvent(event.getType(), intent.getId(),
                metadata == null ? null : metadata.get("orderId"),
                metadata == null ? null : metadata.get("product"),
                intent.getAmount(), intent.getCurrency());
    }

    private StripeClient client() {
        if (!properties.isConfigured()) {
            throw new BizException(BizCodeEnum.PAYMENT_NOT_CONFIGURED);
        }
        StripeClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = new StripeClient(properties.getSecretKey());
                }
                c = client;
            }
        }
        return c;
    }

    private String requiredWebhookSecret() {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BizException(BizCodeEnum.PAYMENT_NOT_CONFIGURED);
        }
        return secret;
    }
}
