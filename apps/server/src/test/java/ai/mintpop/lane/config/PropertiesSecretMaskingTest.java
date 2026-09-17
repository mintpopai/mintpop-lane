package ai.mintpop.lane.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置类的密钥不得出现在 toString() 里。
 *
 * <p>为什么要有这条测试：Lombok 的 {@code @Data} 会给每个字段生成 toString，一旦某个
 * Properties 对象随异常栈或 debug 日志被打印，密钥就明文落进日志。靠「记得加
 * {@code @ToString.Exclude}」守不住——加新字段、或有人顺手去掉注解，都不会有人发现。
 *
 * <p>为什么验行为而不是验注解：Lombok 注解是 {@code RetentionPolicy.SOURCE} 的，
 * 运行时反射根本看不到 {@code @ToString.Exclude}。所以这里给每个字段塞进可辨认的哨兵值、
 * 调一次真实的 toString()，直接断言哨兵没漏出来——这同时也证明了注解确实生效，
 * 而不只是「写了注解」。
 *
 * <p>覆盖面是扫出来的而非写死的：新增的 Properties 类、新增的疑似密钥字段都会自动纳入，
 * 漏加 {@code @ToString.Exclude} 时这条测试会红。确实不敏感的字段加进 {@link #PUBLIC_BY_DESIGN}
 * 并在那里写清理由。
 */
class PropertiesSecretMaskingTest {

    /** 配置类所在包 */
    private static final String CONFIG_PACKAGE = "ai.mintpop.lane.config";

    /** 字段名长这样就按密钥对待。宁可误伤——误伤的代价是往下面的白名单加一行并写明理由 */
    private static final Pattern SECRET_LIKE =
            Pattern.compile("secret|key|token|password|credential", Pattern.CASE_INSENSITIVE);

    /**
     * 名字像密钥、但确实可以公开的字段，格式为「类名.字段名」。
     * 每加一条都要能说清为什么它不敏感。
     */
    private static final Set<String> PUBLIC_BY_DESIGN = Set.of(
            // Stripe 的 publishable key 本就要下发给前端初始化 Stripe.js，不是密钥
            "PaymentProperties.publishableKey",
            // Anthropic 的公开 OAuth 端点地址（带默认值写在代码里），名字含 token 而已，不是凭据
            "ClaudeOAuthProperties.tokenUrl"
    );

    @Test
    @DisplayName("所有配置类里名字像密钥的字段，值都不出现在 toString() 里")
    void secretLikeFieldsAreMaskedInToString() throws Exception {
        List<Class<?>> propertiesClasses = scanPropertiesClasses();
        // 扫不到就说明扫描条件写错了，否则这条测试会变成「什么都没测」的空壳
        assertThat(propertiesClasses)
                .as("应当扫到 %s 包下的 Properties 类", CONFIG_PACKAGE)
                .isNotEmpty();

        List<String> leaked = new ArrayList<>();
        for (Class<?> type : propertiesClasses) {
            Object instance = type.getDeclaredConstructor().newInstance();
            List<Field> stringFields = stringFieldsOf(type);
            // 每个字段塞一个独一无二的哨兵，漏出来时能直接认出是谁
            for (Field field : stringFields) {
                field.setAccessible(true);
                field.set(instance, sentinelFor(field));
            }
            String rendered = instance.toString();
            for (Field field : stringFields) {
                String qualified = type.getSimpleName() + "." + field.getName();
                boolean mustBeMasked = SECRET_LIKE.matcher(field.getName()).find()
                        && !PUBLIC_BY_DESIGN.contains(qualified);
                if (mustBeMasked && rendered.contains(sentinelFor(field))) {
                    leaked.add(qualified);
                }
            }
        }

        assertThat(leaked)
                .as("这些字段的值出现在了 toString() 里：给它们加 @ToString.Exclude，"
                        + "或者确认它不敏感后加进 PUBLIC_BY_DESIGN 并写明理由")
                .isEmpty();
    }

    @Test
    @DisplayName("非密钥字段仍然留在 toString() 里，排错时看得到")
    void nonSecretFieldsStayVisible() throws Exception {
        StorageProperties props = new StorageProperties();
        props.setBucket("mintpop-lane-assets");
        props.setPublicBaseUrl("https://assets.lane.mintpop.ai");
        props.setSecretAccessKey("SHOULD_NOT_APPEAR");

        String rendered = props.toString();

        assertThat(rendered).contains("mintpop-lane-assets", "https://assets.lane.mintpop.ai");
        assertThat(rendered).doesNotContain("SHOULD_NOT_APPEAR");
    }

    /** 扫出配置包下所有以 Properties 结尾的类 */
    private List<Class<?>> scanPropertiesClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Properties")));
        List<Class<?>> classes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(CONFIG_PACKAGE)) {
            classes.add(Class.forName(candidate.getBeanClassName()));
        }
        return classes;
    }

    /** 只看实例字段里的 String：静态常量与非字符串字段不承载密钥 */
    private List<Field> stringFieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static String sentinelFor(Field field) {
        return "SENTINEL_VALUE_OF_" + field.getName();
    }
}
