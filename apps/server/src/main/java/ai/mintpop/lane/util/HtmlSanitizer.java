package ai.mintpop.lane.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 富文本 HTML 净化器：白名单剥离脚本、事件属性与危险协议。
 * 收口在「入库前」这一处——库里只存干净 HTML，换任何客户端都绕不过去。
 */
@Component
public class HtmlSanitizer {

    /** relaxed 覆盖常规排版标签；额外放行 a 的 target/rel，供前端外链新开标签页 */
    private static final Safelist SAFELIST = Safelist.relaxed()
            // TipTap 的删除线产出 <s>、分隔线产出 <hr>，两者都不在 jsoup relaxed 的默认表里
            //（relaxed 只有 HTML4 的 <strike>），不显式放行会被静默剥掉
            .addTags("s", "hr")
            .addAttributes("a", "target", "rel")
            // 控制台是 https 页面，http 图会被当混合内容拦掉；套餐图字段前后端都强制 https，
            // 富文本插图这条路也收齐，顺带管住历史数据与将来可能出现的别的写入口
            .removeProtocols("img", "src", "http");

    /** 保留原样输出：富文本由编辑器生成，不需要 jsoup 再缩进换行（会在 pre/code 里引入空白） */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    /**
     * 净化富文本；输入为空白、或净化后不剩任何内容时返回 null（与其它可选字段一致，库里不混存空串）。
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
