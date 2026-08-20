package ai.mintpop.lane.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 桌面端返回落地页。OAuth 回调的最后一跳如果裸 302 到自定义 scheme，
 * 浏览器会以「重定向无用户手势」为由静默拦截外部协议（Logto 已有会话、
 * 全链路零点击时必现），标签页停在起始 URL 上毫无反应。
 * 业内标准做法（Slack/Zoom/VS Code 同款）：渲染一个 HTML 落地页，
 * 脚本自动尝试跳深链，页面同时给一个可点击按钮兜底——点击即用户手势，
 * 浏览器必定弹出「打开应用」授权框。
 */
@Component
public class DesktopReturnPage {

    /** 桌面端深链回调地址，与桌面端 tauri-plugin-deep-link 注册的 scheme 逐字一致（反域名形态，见 RFC 8252） */
    static final String DESKTOP_CALLBACK = "ai.mintpop.lane://callback";

    /** 登录成功：深链带一次性 ticket 与 state 回桌面端 */
    public void renderSuccess(HttpServletResponse response, String ticket, String state) throws IOException {
        String deepLink = DESKTOP_CALLBACK
                + "?ticket=" + UriUtils.encodeQueryParam(ticket, StandardCharsets.UTF_8)
                + "&state=" + UriUtils.encodeQueryParam(state, StandardCharsets.UTF_8);
        render(response, deepLink, "登录成功", "正在返回 MintPop Lane，若浏览器询问是否打开应用请允许。");
    }

    /** 登录失败：深链带 error 标记回桌面端，让登录页停止空等 */
    public void renderFailure(HttpServletResponse response, String state) throws IOException {
        String deepLink = DESKTOP_CALLBACK
                + "?error=login_failed&state=" + UriUtils.encodeQueryParam(state, StandardCharsets.UTF_8);
        render(response, deepLink, "登录未完成", "点击下方按钮回到应用后重试。");
    }

    private void render(HttpServletResponse response, String deepLink, String heading, String hint)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        // 深链只出现在按钮 href 这一处，脚本从按钮上读，不存在两份拼接漂移
        response.getWriter().write("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>MintPop Lane</title>
                <style>
                  body { display: flex; align-items: center; justify-content: center; min-height: 100vh;
                         margin: 0; font-family: system-ui, sans-serif; background: #fafafa; color: #333; }
                  main { text-align: center; padding: 24px; }
                  h1 { font-size: 22px; margin: 0 0 12px; }
                  p { color: #666; line-height: 1.7; margin: 0 0 24px; }
                  a { display: inline-block; padding: 10px 24px; border-radius: 6px;
                      background: #333; color: #fff; text-decoration: none; font-size: 14px; }
                </style>
                </head>
                <body>
                <main>
                <h1>%s</h1>
                <p>%s<br>本页可随后关闭。</p>
                <a id="open" href="%s">打开 MintPop Lane</a>
                </main>
                <script>location.href = document.getElementById("open").getAttribute("href");</script>
                </body>
                </html>
                """.formatted(escapeHtml(heading), escapeHtml(hint), escapeHtml(deepLink)));
    }

    /** 最小 HTML 转义。深链经查询串编码后本已无危险字符，这里是纵深防御 */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
