package ai.mintpop.lane.parser;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubYamlParserTest {

    private final SubYamlParser parser = new SubYamlParser();

    private String sampleYaml() {
        try (InputStream in = getClass().getResourceAsStream("/sub/sample.yaml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("解析出 proxies 里的有效节点，忽略 proxy-groups 与规则段，缺关键字段的条目跳过")
    void parsesValidNodes() {
        List<SubNode> nodes = parser.parse(sampleYaml());

        // 5 个条目里 1 个缺 port 被跳过，剩 4 个；proxy-groups 里的「自动选择」不在其中
        assertThat(nodes).hasSize(4);
        assertThat(nodes).extracting(SubNode::sourceName)
                .containsExactly("剩余流量：121.54 GB", "套餐到期：2027-04-30", "香港 IEPL-01", "[境外用户专用]GPT01");

        SubNode hk = nodes.get(2);
        assertThat(hk.sourceType()).isEqualTo("anytls");
        assertThat(hk.serverAddr()).isEqualTo("hk02a.example.com");
        assertThat(hk.port()).isEqualTo(35356);
        // params 是去掉 name 的整份参数，嵌套结构原样保留
        assertThat(hk.params()).containsEntry("password", "uuid-秘密-1").containsEntry("udp", true)
                .doesNotContainKey("name");
        assertThat(nodes.get(3).params().get("reality-opts")).isEqualTo(Map.of("public-key", "pk-value"));
    }

    @Test
    @DisplayName("名称含流量/到期等关键词的条目标记为疑似信息条目")
    void marksInfoEntries() {
        List<SubNode> nodes = parser.parse(sampleYaml());
        assertThat(nodes.get(0).suspectedInfo()).isTrue();
        assertThat(nodes.get(1).suspectedInfo()).isTrue();
        assertThat(nodes.get(2).suspectedInfo()).isFalse();
        assertThat(nodes.get(3).suspectedInfo()).isFalse();
    }

    @Test
    @DisplayName("非 YAML、无 proxies、proxies 为空或全无效时，一律报订阅解析失败")
    void parseFailureShapes() {
        for (String bad : List.of("YW55dGxzOi8vbm90LXlhbWw=", "mixed-port: 7890", "proxies: []",
                "proxies:\n    - { name: '只有名字' }")) {
            assertThatThrownBy(() -> parser.parse(bad))
                    .isInstanceOf(BizException.class)
                    .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_PARSE_FAILED);
        }
    }
}
