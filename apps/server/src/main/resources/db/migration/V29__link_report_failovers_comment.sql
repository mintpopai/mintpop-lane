-- 只改列注释，类型/默认值与 V22 建表时完全一致：说明清楚 failovers 的已知限制
-- （机场订阅与第一跳分配四期后，内层改为 url-test，该计数含义已变化，见设计文档第六节）
ALTER TABLE link_report
    MODIFY COLUMN failovers INT NOT NULL DEFAULT 0 COMMENT '窗口内该故障域对应的 fallback 组 now 字段发生变化的次数，即故障转移次数。已知限制：内层改为 url-test 后，该计数也含"择优切换"，不再单纯是原节点不可用才切换；且不含跨订阅（外层 us-front）的切换';
