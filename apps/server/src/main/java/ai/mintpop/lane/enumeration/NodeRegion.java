package ai.mintpop.lane.enumeration;

import lombok.Getter;

import java.util.regex.Pattern;

/**
 * 第一跳节点的筛选地区。订阅配置里只有入口（中转机），没有出口，判定只能看节点名。
 * 每个成员自带一条正则：只认国旗、带方括号的国别码与完整中文国名，裸国别码/单字简称一律不认——
 * 裸 US 会命中 Russia / Belarus / Cyprus，单个「美」字会命中完美 / 南美；漏判只是少一个候选，
 * 误判会把非目标地区的节点分给用户导致落地拒连。现在只有美国，加地区就是加一个成员。
 */
@Getter
public enum NodeRegion {
    US("美国", "(?i)(🇺🇸|\\[US]|【US】|美国)");

    private final String displayName;
    private final Pattern pattern;

    NodeRegion(String displayName, String regex) {
        this.displayName = displayName;
        this.pattern = Pattern.compile(regex);
    }

    /** 节点名是否落在本地区；null 视为不匹配 */
    public boolean matches(String nodeName) {
        return nodeName != null && pattern.matcher(nodeName).find();
    }
}
