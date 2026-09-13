package ai.mintpop.lane.enumeration;

import java.util.Locale;

/**
 * 飞书卡片标题色。颜色表达事件性质：好消息绿、要人处理的告警橙。
 * 与 NodeProtocol.mihomoType() 同理：feishuName() 是对接外部协议的取值，枚举名本身仍按规范大写。
 */
public enum FeishuCardTemplate {

    GREEN,
    ORANGE,
    RED;

    /** 飞书 header.template 字段的取值，是枚举名的小写形式 */
    public String feishuName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
