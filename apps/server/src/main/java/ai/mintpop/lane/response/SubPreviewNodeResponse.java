package ai.mintpop.lane.response;

/** 订阅预览里的一个条目。只有展示字段，敏感参数不出现。 */
public record SubPreviewNodeResponse(
        String sourceName,
        String sourceType,
        String serverAddr,
        Integer port,
        /** 名称像「剩余流量/到期时间」的信息假条目，前端默认不勾选 */
        boolean suspectedInfo,
        /** 该分组内是否已入池（按 sourceName 匹配）；新链接预览恒为 false */
        boolean existed
) {
}
