package ai.mintpop.lane.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    private StorageProperties filled() {
        StorageProperties props = new StorageProperties();
        props.setAccountId("acc");
        props.setAccessKeyId("ak");
        props.setSecretAccessKey("sk");
        props.setBucket("mintpop-lane-assets");
        props.setPublicBaseUrl("https://assets.lane.mintpop.ai");
        return props;
    }

    @Test
    @DisplayName("五项齐全才算配置完整")
    void configuredOnlyWhenAllPresent() {
        assertThat(filled().isConfigured()).isTrue();
        assertThat(new StorageProperties().isConfigured()).isFalse();
    }

    @Test
    @DisplayName("任一项留空串也算没配：YAML 里写了键但没填值是常见形态")
    void blankValueCountsAsMissing() {
        StorageProperties props = filled();
        props.setAccessKeyId("   ");
        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("端点按账户 ID 拼成 R2 的 S3 兼容地址")
    void buildsEndpointFromAccountId() {
        assertThat(filled().endpoint()).isEqualTo("https://acc.r2.cloudflarestorage.com");
    }
}
