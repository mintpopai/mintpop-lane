package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 额度档位的**真落库**验证：用真实 MySQL 跑一遍「置档 → 用量回落 → 清档」，每一步都重新从库里读。
 * <p>
 * 为什么必须是集成测试：{@link TrafficAlertServiceTest} 用的是 mock 仓储，它断言的是
 * 「DTO 内存字段变成 null」+「update 被调了一次」——这两条在 node_group.traffic_alerted_pct
 * 少了 {@code @TableField(updateStrategy = ALWAYS)} 时**照样全绿**（MyBatis-Plus 默认 NOT_NULL
 * 策略会把这一列整个从 UPDATE 语句里剔掉，是一次静默空操作）。而每轮刷新的 DTO 都从库里重读，
 * 内存里那个 null 活不过一轮，于是分组会永久失去额度告警且不报错。
 * 本用例只信「重新 select 出来的值」，是唯一能挡住这个 bug 的断言形态。
 */
class TrafficAlertServicePersistenceTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NodeGroupRepository groupRepository;

    @Autowired
    private TrafficAlertService service;

    @BeforeEach
    void setUp() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE proxy_node");
        jdbc.execute("TRUNCATE TABLE node_group");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** total=100，used 即百分比，省去换算；expiresAt 留空，只考察额度档位这一条路径 */
    private SubFetchResult used(long percent) {
        return new SubFetchResult("proxies: []", null, percent, 100L, null);
    }

    private Long createGroup() {
        NodeGroupDto group = new NodeGroupDto();
        group.setName("TaiShan Net");
        group.setSubUrl("https://sub.example.com/c?token=t");
        return groupRepository.create(group);
    }

    /** 直接读原始列，绕开 DTO：要断言的就是「库里那一格」，不是内存对象 */
    private Integer readAlertedPctColumn(Long groupId) {
        return jdbc.queryForObject(
                "SELECT traffic_alerted_pct FROM node_group WHERE id = ?", Integer.class, groupId);
    }

    @Test
    @DisplayName("跨档落库、用量回落后清档：重新从库里读出来那一列必须是 NULL")
    void clearsAlertedThresholdInDatabaseAfterUsageDrops() {
        Long groupId = createGroup();

        // 1) 冲到 95% 档：档位要真的写进库
        service.checkAndNotify(groupRepository.findById(groupId).orElseThrow(), used(96));
        assertThat(readAlertedPctColumn(groupId)).isEqualTo(95);

        // 2) 月初重置、用量掉回 5%：清档同样要真的写进库
        //    这里刻意重新 findById 取一份全新的 DTO——线上每轮刷新都是这样从库里重读的，
        //    上一步留在内存里的那个对象活不过一轮，不能拿它来断言
        service.checkAndNotify(groupRepository.findById(groupId).orElseThrow(), used(5));
        assertThat(readAlertedPctColumn(groupId)).isNull();
        assertThat(groupRepository.findById(groupId).orElseThrow().getTrafficAlertedPct()).isNull();

        // 3) 清档之后再跨 80% 档，能重新推、重新落档——这才是清档要保住的能力
        service.checkAndNotify(groupRepository.findById(groupId).orElseThrow(), used(85));
        assertThat(readAlertedPctColumn(groupId)).isEqualTo(80);
    }
}
