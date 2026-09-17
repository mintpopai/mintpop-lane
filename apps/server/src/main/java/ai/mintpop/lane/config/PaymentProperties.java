package ai.mintpop.lane.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stripe 支付配置（payment.stripe.*）：密钥来自 jar 外 config/application.yml，不入库不进仓库。
 * 未配置时支付入口整体降级（checkout-info 返回空方法列表），不影响启动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.stripe")
public class PaymentProperties {

    /** 我方支付子方式 → Stripe payment_method_types（与前端 payment.ts 的映射表逐字一致） */
    private static final Map<String, String> PM_TYPE_MAPPING =
            Map.of("wxpay", "wechat_pay", "alipay", "alipay", "card", "card");

    /** Stripe secret key（敏感，sk_ 开头）。排除出 toString——它能直接调 Stripe API 发起扣款与退款 */
    @ToString.Exclude
    private String secretKey;
    /** Stripe webhook 签名密钥（敏感，whsec_ 开头）。排除出 toString——泄露后可伪造「已支付」事件骗过履约 */
    @ToString.Exclude
    private String webhookSecret;
    /** Stripe publishable key（下发前端初始化 Stripe.js，非敏感） */
    private String publishableKey;
    /** 启用的支付子方式，逗号分隔（我方命名：card / alipay / wxpay） */
    private String supportedTypes = "card,alipay,wxpay";
    /** 卡账单描述符后缀，显示为 MINTPOP* LANE；留空则不传 */
    private String statementDescriptorSuffix = "LANE";
    /** 业务线标记：写入 PaymentIntent 的 metadata.product，webhook 据此认领本业务事件（同一 Stripe 账号多业务共用） */
    private String productCode = "lane";

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank()
                && publishableKey != null && !publishableKey.isBlank();
    }

    /** 配置的子方式经映射表转 Stripe 类型；为空一律回退 card */
    public List<String> resolvePaymentMethodTypes() {
        List<String> types = Arrays.stream(supportedTypes == null ? new String[0] : supportedTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(PM_TYPE_MAPPING::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return types.isEmpty() ? List.of("card") : types;
    }
}
