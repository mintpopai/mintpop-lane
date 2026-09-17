package ai.mintpop.lane.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * storage.r2 五项都非空白才装配存储 bean（判据与 StorageProperties.isConfigured() 同一份）。
 * 不用 @ConditionalOnProperty：它只看「属性存在」，YAML 里把值留空（access-key-id:）也算存在，
 * 会走到 AwsBasicCredentials.create("", "") 抛异常、整个应用起不来，与「整段可选、不配则优雅降级」相悖。
 */
public class StorageConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind("storage.r2", StorageProperties.class)
                .map(StorageProperties::isConfigured)
                .orElse(false);
    }
}
