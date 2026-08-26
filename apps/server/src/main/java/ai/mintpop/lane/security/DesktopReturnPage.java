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
 *
 * <p>视觉与官网（apps/website/src/styles.css）同源：同一套品牌 token、同样的
 * 浅色单主题。页面主体是一条「lane」——起点圆点经虚线车道通向 Lane 应用瓦片，
 * 一枚薄荷光点在其上滑向应用，正是这一页在做的事（把人从浏览器交回桌面端）。
 * 失败时同一条车道从中间断开、光点停在断口、瓦片熄灭，状态不靠颜色单独传达。
 */
@Component
public class DesktopReturnPage {

    /** 桌面端深链回调地址，与桌面端 tauri-plugin-deep-link 注册的 scheme 逐字一致（反域名形态，见 RFC 8252） */
    static final String DESKTOP_CALLBACK = "ai.mintpop.lane://callback";

    /**
     * Lane 应用瓦片，与官网 index.html 引的是同一张：既当浏览器图标，也当车道终点的目的地。
     * 托管在品牌规范站（同在 Cloudflare 后，含中国大陆在内全球可达），不落地到本仓库——
     * 规范站换图这边跟着变，不会与品牌规范漂移。
     */
    static final String APP_ICON = "https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png";

    /** 落地页的两种落点：文案与视觉随之切换，深链参数由调用方拼好传进来 */
    private enum Outcome {
        // 两句写得差不多长：卡片里正好排成两行，配上 text-wrap: balance 断点落在句号后，
        // 不会把「浏览器」这样的词从中间劈开（中文没有词边界，浏览器可在任意两字间断行）
        SUCCESS("state-ok", "登录成功",
                "正在把你送回 MintPop Lane。浏览器询问时选择允许。"),
        FAILURE("state-fail", "登录未完成",
                "没能完成这次登录。回到 MintPop Lane 再试一次。");

        /** 挂在 body 上的状态类名，CSS 据此切换车道形态 */
        private final String bodyClass;
        private final String heading;
        private final String hint;

        Outcome(String bodyClass, String heading, String hint) {
            this.bodyClass = bodyClass;
            this.heading = heading;
            this.hint = hint;
        }
    }

    /** 登录成功：深链带一次性 ticket 与 state 回桌面端 */
    public void renderSuccess(HttpServletResponse response, String ticket, String state) throws IOException {
        String deepLink = DESKTOP_CALLBACK
                + "?ticket=" + UriUtils.encodeQueryParam(ticket, StandardCharsets.UTF_8)
                + "&state=" + UriUtils.encodeQueryParam(state, StandardCharsets.UTF_8);
        render(response, deepLink, Outcome.SUCCESS);
    }

    /** 登录失败：深链带 error 标记回桌面端，让登录页停止空等 */
    public void renderFailure(HttpServletResponse response, String state) throws IOException {
        String deepLink = DESKTOP_CALLBACK
                + "?error=login_failed&state=" + UriUtils.encodeQueryParam(state, StandardCharsets.UTF_8);
        render(response, deepLink, Outcome.FAILURE);
    }

    private void render(HttpServletResponse response, String deepLink, Outcome outcome) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        // 样式整段作为 %s 的实参传入：String.formatted 不会二次解析实参，
        // CSS 里的 % （100%、渐变位置、keyframes）才能照常写，不必写成 %%
        // 深链只出现在按钮 href 这一处，脚本从按钮上读，不存在两份拼接漂移
        response.getWriter().write("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="theme-color" content="#17d1a7">
                <meta name="robots" content="noindex">
                <title>%s · MintPop Lane</title>
                <link rel="preconnect" href="https://standards.mintpop.ai">
                <link rel="icon" type="image/png" sizes="512x512" href="%s">
                <link rel="apple-touch-icon" href="%s">
                <style>%s</style>
                </head>
                <body class="%s">
                <main class="card">
                <div class="lane" aria-hidden="true">
                <span class="origin"></span>
                <span class="track"><span class="spark"></span></span>
                <span class="dest"><img src="%s" alt="" width="56" height="56"></span>
                </div>
                <h1>%s</h1>
                <p class="hint">%s</p>
                <a class="btn" id="open" href="%s">打开 MintPop Lane</a>
                <p class="foot">回到应用后，这个页面就可以关掉了。</p>
                </main>
                <script>location.href = document.getElementById("open").getAttribute("href");</script>
                </body>
                </html>
                """.formatted(
                escapeHtml(outcome.heading), APP_ICON, APP_ICON, STYLE, outcome.bodyClass,
                APP_ICON, escapeHtml(outcome.heading), escapeHtml(outcome.hint), escapeHtml(deepLink)));
    }

    /**
     * 页面样式。取值全部对齐官网 styles.css 的品牌 token（薄荷绿、压深后的可读绿、
     * 圆角与按钮规格），只是这一页是服务端单文件渲染，没有构建产物可引，故就地内联。
     * 字体不外链（Google Fonts 在中国大陆不可达，见全球可达性规范），走系统字体栈：
     * 西文交给各平台 UI 字体，中文回退与官网同一串。
     */
    private static final String STYLE = """
            :root {
              --mint: #17d1a7;
              --brand-text: #0a8265;
              --brand-strong: #087257;
              --ink: #0b0b0c;
              --ink-2: #4b5563;
              --ink-3: #6b7280;
              --bg-soft: #f4f8f6;
              --bg-mint: #edfaf5;
              --line: #e5e7eb;
              --line-mint: #cdeee3;
              --warn: #92610b;
              --muted-soft: #f1f3f2;
              color-scheme: light;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              min-height: 100vh;
              min-height: 100dvh;
              display: grid;
              place-items: center;
              padding: 24px;
              background: var(--bg-soft);
              color: var(--ink);
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui,
                "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif;
              font-size: 16px;
              line-height: 1.65;
              -webkit-font-smoothing: antialiased;
            }
            /* 顶部一层极淡的薄荷光晕，把视线压到卡片上；失败时整层撤掉 */
            body::before {
              content: "";
              position: fixed;
              inset: 0;
              pointer-events: none;
              background: radial-gradient(62% 46% at 50% 0%, var(--bg-mint), transparent 72%);
            }
            .state-fail::before { display: none; }

            .card {
              position: relative;
              width: min(420px, 100%);
              padding: 44px 40px 34px;
              text-align: center;
              background: #fff;
              border: 1px solid var(--line);
              border-radius: 20px;
              box-shadow: 0 1px 2px rgba(11, 11, 12, 0.04), 0 20px 44px -26px rgba(11, 11, 12, 0.22);
            }

            /* —— 签名元素：一条 lane，从起点滑向 Lane 应用 —— */
            .lane {
              display: flex;
              align-items: center;
              justify-content: center;
              gap: 16px;
              margin-bottom: 30px;
            }
            .origin {
              flex: none;
              width: 11px;
              height: 11px;
              border-radius: 50%;
              border: 2px solid var(--line-mint);
              background: #fff;
            }
            .track {
              position: relative;
              flex: none;
              width: 120px;
              height: 2px;
            }
            /* 车道分两段画：成功时两段贴合成一条完整虚线，失败时中间让出断口 */
            .track::before,
            .track::after {
              content: "";
              position: absolute;
              top: 0;
              height: 2px;
              background: repeating-linear-gradient(90deg, var(--line-mint) 0 5px, transparent 5px 11px);
            }
            .track::before { left: 0; right: 50%; }
            .track::after { left: 50%; right: 0; }
            .spark {
              position: absolute;
              top: 50%;
              left: 0;
              width: 10px;
              height: 10px;
              margin: -5px 0 0 -5px;
              border-radius: 50%;
              background: var(--mint);
              box-shadow: 0 0 0 4px rgba(23, 209, 167, 0.18);
            }
            .dest {
              flex: none;
              display: grid;
              place-items: center;
              width: 56px;
              height: 56px;
              border-radius: 15px;
              background: var(--bg-mint);
              box-shadow: 0 8px 20px -6px rgba(10, 130, 101, 0.26);
            }
            .dest img { display: block; width: 56px; height: 56px; border-radius: 15px; }

            /* 断口：右半段退成中性灰虚线，光点停在断口前，应用瓦片熄灭 */
            .state-fail .track::before { right: calc(50% + 11px); }
            .state-fail .track::after {
              left: calc(50% + 11px);
              background: repeating-linear-gradient(90deg, var(--line) 0 5px, transparent 5px 11px);
            }
            .state-fail .spark {
              left: calc(50% - 11px);
              width: 11px;
              height: 11px;
              margin: -5.5px 0 0 -5.5px;
              background: #fff;
              border: 2px solid var(--warn);
              box-shadow: none;
            }
            .state-fail .dest { background: var(--muted-soft); box-shadow: none; }
            .state-fail .dest img { opacity: 0.45; }

            h1 {
              margin: 0 0 10px;
              font-size: 24px;
              font-weight: 650;
              letter-spacing: -0.015em;
              line-height: 1.25;
            }
            .hint {
              margin: 0 0 26px;
              color: var(--ink-2);
              font-size: 15px;
              text-wrap: balance;
            }
            .btn {
              display: inline-flex;
              align-items: center;
              justify-content: center;
              width: 100%;
              padding: 13px 24px;
              border-radius: 10px;
              background: var(--brand-text);
              color: #fff;
              font-size: 16px;
              font-weight: 600;
              text-decoration: none;
              transition: background 0.15s ease, transform 0.15s ease;
            }
            .btn:hover { background: var(--brand-strong); }
            .btn:active { transform: translateY(1px); }
            .foot {
              margin: 18px 0 0;
              color: var(--ink-3);
              font-size: 13px;
            }
            :focus-visible {
              outline: 2px solid var(--brand-text);
              outline-offset: 3px;
              border-radius: 4px;
            }

            /* 动效只动 opacity/transform；用户偏好减少动效时，光点静止停在车道中段 */
            @media (prefers-reduced-motion: no-preference) {
              .card { animation: rise 0.6s cubic-bezier(0.16, 1, 0.3, 1) both; }
              .state-ok .spark { animation: glide 1.7s cubic-bezier(0.55, 0, 0.45, 1) infinite; }
              @keyframes rise {
                from { opacity: 0; transform: translateY(14px); }
                to { opacity: 1; transform: none; }
              }
              @keyframes glide {
                from { left: 0; opacity: 0; }
                18% { opacity: 1; }
                82% { opacity: 1; }
                to { left: 100%; opacity: 0; }
              }
            }
            @media (prefers-reduced-motion: reduce) {
              .state-ok .spark { left: calc(50% - 5px); }
            }
            @media (max-width: 420px) {
              .card { padding: 36px 24px 28px; }
              .track { width: 88px; }
            }
            """;

    /** 最小 HTML 转义。深链经查询串编码后本已无危险字符，这里是纵深防御 */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
