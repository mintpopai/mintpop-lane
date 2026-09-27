package ai.mintpop.lane.util;

import java.util.regex.Pattern;

/**
 * 按节点名判定它是不是「落地在美国」。
 *
 * 订阅配置里只有入口（中转机），没有出口，判定只能看节点名。所用机场是可枚举的，
 * 命名形如 {@code 🇺🇸[US]Santa Clara 01-GPT优化}，故规则写死为：名字里有美国国旗 🇺🇸，
 * 或带方括号的国别码 {@code [US]} / {@code 【US】}，二者命中其一即算。
 * 其余写法（United States、美国、美西、裸 US 等）一律不认——裸 US 会命中
 * Russia / Belarus / Cyprus / Bonus，「美」字会命中完美 / 南美 / 拉美；
 * 漏判只是少一个候选、由人复筛兜住，误判会把非美国节点分配给用户导致落地直接拒连。
 * 凡是用它做决策的地方都要把判定结果摆给人看，不做纯自动决策。改规则前先想清楚方向。
 */
public final class UsLandingNodes {

    private static final Pattern US_NODE = Pattern.compile("(?i)(🇺🇸|\\[US]|【US】)");

    private UsLandingNodes() {
    }

    public static boolean isUsLanding(String nodeName) {
        return nodeName != null && US_NODE.matcher(nodeName).find();
    }
}
