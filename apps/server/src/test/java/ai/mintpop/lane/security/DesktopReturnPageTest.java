package ai.mintpop.lane.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桌面端返回落地页：OAuth 回调不再裸 302 跳自定义 scheme（浏览器会以
 * 「重定向无用户手势」为由静默拦截外部协议），而是渲染一个 HTML 页面——
 * 脚本自动尝试跳深链，并提供可点击的按钮兜底（点击即用户手势，浏览器必弹授权框）。
 */
class DesktopReturnPageTest {

    private final DesktopReturnPage page = new DesktopReturnPage();

    @Test
    @DisplayName("成功页：200 HTML，含深链按钮与自动跳转脚本")
    void successPageHasDeepLinkButtonAndAutoRedirectScript() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        page.renderSuccess(response, "t-abc", "st-1");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        String html = response.getContentAsString();
        // 深链落在可点击元素的 href 上：用户点击 = 用户手势，浏览器必出「打开应用」弹窗
        assertThat(html).contains("href=\"ai.mintpop.lane://callback?ticket=t-abc&amp;state=st-1\"");
        assertThat(html).contains("打开 MintPop Lane");
        // 自动跳转脚本从该元素读 href，深链只在页面里出现一处，不存在两份拼接漂移
        assertThat(html).contains("<script>");
        assertThat(html).contains("登录成功");
    }

    @Test
    @DisplayName("两态都带浏览器图标，且与官网引的是同一张 Lane 应用瓦片")
    void bothPagesLinkTheSameFaviconAsWebsite() throws Exception {
        MockHttpServletResponse success = new MockHttpServletResponse();
        MockHttpServletResponse failure = new MockHttpServletResponse();

        page.renderSuccess(success, "t-abc", "st-1");
        page.renderFailure(failure, "st-2");

        // 与 apps/website/index.html 的 <link rel="icon"> 逐字同一个地址：官网换图这边跟着变
        String iconLink = "<link rel=\"icon\" type=\"image/png\" sizes=\"512x512\" href=\""
                + DesktopReturnPage.APP_ICON + "\">";
        assertThat(success.getContentAsString()).contains(iconLink);
        assertThat(failure.getContentAsString()).contains(iconLink);
    }

    @Test
    @DisplayName("失败页：深链带 error=login_failed 与 state")
    void failurePageDeepLinkCarriesErrorFlag() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        page.renderFailure(response, "st-2");

        assertThat(response.getStatus()).isEqualTo(200);
        String html = response.getContentAsString();
        assertThat(html).contains("href=\"ai.mintpop.lane://callback?error=login_failed&amp;state=st-2\"");
        assertThat(html).contains("打开 MintPop Lane");
        assertThat(html).doesNotContain("登录成功");
    }

    @Test
    @DisplayName("ticket 与 state 经查询串编码，特殊字符不会破坏 HTML")
    void specialCharsEncodedWithoutBreakingPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        page.renderSuccess(response, "t\"><script>", "st&x=1");

        String html = response.getContentAsString();
        // 原样字符串不得出现在页面里（已被百分号编码），防注入
        assertThat(html).doesNotContain("t\"><script>");
        assertThat(html).doesNotContain("state=st&x=1");
        assertThat(html).contains("ticket=t%22%3E%3Cscript%3E");
    }
}
