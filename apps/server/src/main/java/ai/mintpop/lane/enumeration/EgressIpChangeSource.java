package ai.mintpop.lane.enumeration;

/**
 * 落地出口 IP 变更的来源，决定通知卡片的措辞与标题色：
 * 管理员自己改的只需确认（绿），定时巡检自动回填的是无人参与的变更、要人知道（橙）。
 */
public enum EgressIpChangeSource {

    /** 管理端整体更新接口改了出口 IP（含检测后一键回填） */
    ADMIN("管理端修改", FeishuCardTemplate.GREEN),

    /** 定时巡检发现实际出口与登记不一致，自动以实际值回填（时区同步重解析） */
    EGRESS_CHECK("定时巡检自动回填", FeishuCardTemplate.ORANGE);

    private final String label;
    private final FeishuCardTemplate template;

    EgressIpChangeSource(String label, FeishuCardTemplate template) {
        this.label = label;
        this.template = template;
    }

    /** 卡片「来源」字段的展示文案 */
    public String label() {
        return label;
    }

    /** 卡片标题色 */
    public FeishuCardTemplate template() {
        return template;
    }
}
