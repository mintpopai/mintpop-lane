package ai.mintpop.lane.util;

import java.util.regex.Pattern;

/**
 * 按节点名判定它是不是「落地在美国」。
 *
 * 这是启发式：机场命名不规范时会漏判，所以凡是用它做决策的地方都要把判定结果
 * 原样列出来给人核对，不做纯自动决策。正则**刻意比直觉窄**——裸 US 会命中
 * Russia / Belarus / Cyprus / Bonus，裸「美」会命中完美 / 南美 / 拉美，
 * 漏判只是少一个候选、由人兜住，误判会把非美国节点分配给用户导致落地直接拒连。
 * 收窄的完整理由见 spec §11；改这条正则前先想清楚方向。
 */
public final class UsLandingNodes {

    private static final Pattern US_NODE = Pattern.compile("(?i)(\\[US]|United States|美国|🇺🇸)");

    private UsLandingNodes() {
    }

    public static boolean isUsLanding(String nodeName) {
        return nodeName != null && US_NODE.matcher(nodeName).find();
    }
}
