package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    @DisplayName("常规排版标签原样保留")
    void keepsFormattingTags() {
        String html = "<p>套餐<strong>包含</strong></p><ul><li>5 个席位</li></ul>";
        assertThat(sanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("script 标签连内容一起剥掉")
    void stripsScript() {
        assertThat(sanitizer.sanitize("<p>正文</p><script>alert(1)</script>"))
                .isEqualTo("<p>正文</p>");
    }

    @Test
    @DisplayName("事件属性被剥掉，标签本身保留")
    void stripsEventAttributes() {
        assertThat(sanitizer.sanitize("<img src=\"https://x/a.png\" onerror=\"alert(1)\">"))
                .doesNotContain("onerror")
                .contains("https://x/a.png");
    }

    @Test
    @DisplayName("javascript: 协议的链接地址被剥掉")
    void stripsJavascriptProtocol() {
        assertThat(sanitizer.sanitize("<a href=\"javascript:alert(1)\">点我</a>"))
                .doesNotContain("javascript:");
    }

    @Test
    @DisplayName("外链的 target 与 rel 放行：前端要用它在新标签页打开")
    void keepsLinkTargetAndRel() {
        String html = "<a href=\"https://x\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">文档</a>";
        assertThat(sanitizer.sanitize(html)).contains("target=\"_blank\"").contains("rel=");
    }

    @Test
    @DisplayName("空白输入与净化后不剩内容都归 null，库里不混存空壳")
    void normalizesEmptyToNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("   ")).isNull();
        assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isNull();
    }

    @Test
    @DisplayName("编辑器工具栏能产出的标签都要保留：删除线 <s> 与分隔线 <hr> 不在 jsoup relaxed 默认表里")
    void keepsEditorOnlyTags() {
        assertThat(sanitizer.sanitize("<p>前<s>删除线</s>后</p><hr>"))
                .isEqualTo("<p>前<s>删除线</s>后</p><hr>");
    }

    @Test
    @DisplayName("富文本插图的 http 地址被剥掉，https 原样保留：控制台是 https 页面，http 图会被当混合内容拦掉")
    void stripsHttpImageProtocol() {
        String cleaned = sanitizer.sanitize(
                "<img src=\"http://x/a.png\"><img src=\"https://x/b.png\">");
        assertThat(cleaned).doesNotContain("http://x/a.png").contains("https://x/b.png");
    }
}
