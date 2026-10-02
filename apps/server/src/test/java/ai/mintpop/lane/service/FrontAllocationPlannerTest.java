package ai.mintpop.lane.service;

import ai.mintpop.lane.service.FrontAllocationPlanner.Assignment;
import ai.mintpop.lane.service.FrontAllocationPlanner.Candidate;
import ai.mintpop.lane.service.FrontAllocationPlanner.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.NodeRegion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("第一跳分配算法：场景负载 + 逐位贪心（spec 第四节）")
class FrontAllocationPlannerTest {

    private static final FrontSettings SETTINGS = new FrontSettings(NodeRegion.US, 3, 20);

    // 三家机场 1、2、3，各一个订阅 11、21、31
    private static final long A = 1, B = 2, C = 3;
    private static final long A1 = 11, B1 = 21, C1 = 31;

    private static Slot a1() { return new Slot(A, A1); }
    private static Slot b1() { return new Slot(B, B1); }
    private static Slot c1() { return new Slot(C, C1); }

    private static Assignment user(long id, Slot... slots) {
        return new Assignment(id, List.of(slots));
    }

    private static List<Candidate> threeAirports300() {
        return List.of(new Candidate(A1, A, 300), new Candidate(B1, B, 300), new Candidate(C1, C, 300));
    }

    /** spec 4.4 的四个已有用户 */
    private static List<Assignment> specExistingUsers() {
        return List.of(
                user(1, a1(), b1(), c1()),
                user(2, a1(), c1(), b1()),
                user(3, b1(), a1(), c1()),
                user(4, c1(), a1(), b1()));
    }

    @Test
    @DisplayName("F 为空时场景负载就是主用人数")
    void emptyScenarioIsPrimaryCount() {
        assertThat(FrontAllocationPlanner.scenarioLoad(Set.of(), specExistingUsers()))
                .containsEntry(A1, 2).containsEntry(B1, 1).containsEntry(C1, 1);
    }

    @Test
    @DisplayName("按挂掉的机场集合算，与它们在列表里的顺序无关")
    void scenarioDependsOnSetNotOrder() {
        List<Assignment> users = List.of(user(1, a1(), b1(), c1()), user(2, b1(), a1(), c1()));
        assertThat(FrontAllocationPlanner.scenarioLoad(Set.of(A, B), users)).containsEntry(C1, 2);
    }

    @Test
    @DisplayName("列表更短的用户也会被正确计入：[A, C] 在 {A, B} 挂掉时落到 C")
    void shorterListsAreCounted() {
        List<Assignment> users = List.of(user(1, a1(), c1()), user(2, c1()));
        assertThat(FrontAllocationPlanner.scenarioLoad(Set.of(A, B), users)).containsEntry(C1, 2);
    }

    @Test
    @DisplayName("列表里的机场全挂了的用户不计入任何订阅")
    void usersWithNoSurvivingAirportAreIgnored() {
        List<Assignment> users = List.of(user(1, a1(), b1()));
        assertThat(FrontAllocationPlanner.scenarioLoad(Set.of(A, B), users)).isEmpty();
    }

    @Test
    @DisplayName("spec 4.4 的例子：新用户分到 [B1, C1, A1]")
    void specExample() {
        assertThat(FrontAllocationPlanner.plan(specExistingUsers(), threeAirports300(), SETTINGS))
                .containsExactly(b1(), c1(), a1());
    }

    @Test
    @DisplayName("主用名额全满时返回空列表（300M 容量 15，已有 15 个主用）")
    void emptyWhenAllPrimaryFull() {
        List<Assignment> full = java.util.stream.LongStream.rangeClosed(1, 15)
                .mapToObj(id -> user(id, a1())).toList();
        assertThat(FrontAllocationPlanner.plan(full, List.of(new Candidate(A1, A, 300)), SETTINGS)).isEmpty();
    }

    @Test
    @DisplayName("主用只从未满的订阅里选；满额订阅仍可当备用")
    void fullSubscriptionStillUsableAsBackup() {
        List<Assignment> aFull = java.util.stream.LongStream.rangeClosed(1, 15)
                .mapToObj(id -> user(id, a1())).toList();
        List<Slot> planned = FrontAllocationPlanner.plan(aFull,
                List.of(new Candidate(A1, A, 300), new Candidate(B1, B, 300)), SETTINGS);
        assertThat(planned).containsExactly(b1(), a1());
    }

    @Test
    @DisplayName("可用机场少于 3 家时列表变短，不报错")
    void shortListWhenFewerAirportsThanMax() {
        assertThat(FrontAllocationPlanner.plan(List.of(), List.of(new Candidate(A1, A, 300)), SETTINGS))
                .containsExactly(a1());
    }

    @Test
    @DisplayName("同一家机场在列表里最多出现一次：同机场的第二个订阅不会被选作备用")
    void neverRepeatsAnAirport() {
        List<Slot> planned = FrontAllocationPlanner.plan(List.of(),
                List.of(new Candidate(A1, A, 300), new Candidate(12, A, 300)), SETTINGS);
        assertThat(planned).hasSize(1);
    }

    @Test
    @DisplayName("按「人数/带宽」比较：大带宽订阅按比例多接人")
    void comparesByLoadPerBandwidth() {
        // A1 600M 已有 3 人（(3+1)/600），B1 300M 已有 2 人（(2+1)/300）：A1 更空
        List<Assignment> users = List.of(user(1, a1()), user(2, a1()), user(3, a1()),
                user(4, b1()), user(5, b1()));
        List<Slot> planned = FrontAllocationPlanner.plan(users,
                List.of(new Candidate(A1, A, 600), new Candidate(B1, B, 300)), SETTINGS);
        assertThat(planned.get(0)).isEqualTo(a1());
    }

    @Test
    @DisplayName("平手按订阅 id 小的优先，结果确定可复现")
    void tieBreaksBySubscriptionId() {
        assertThat(FrontAllocationPlanner.plan(List.of(), threeAirports300(), SETTINGS).get(0)).isEqualTo(a1());
    }

    @Test
    @DisplayName("主用容量 = 带宽 / 每人带宽 向下取整")
    void primaryCapacityFloors() {
        assertThat(new Candidate(A1, A, 110).primaryCapacity(20)).isEqualTo(5);
        assertThat(FrontAllocationPlanner.primaryCapacity(300, 20)).isEqualTo(15);
    }

    @Test
    @DisplayName("每人机场数从设置读：设 2 时列表最长 2；每人带宽设 50 时 300M 订阅只有 6 个主用名额")
    void honoursSettings() {
        List<Candidate> three = List.of(new Candidate(1, 10, 300), new Candidate(2, 20, 300), new Candidate(3, 30, 300));
        assertThat(FrontAllocationPlanner.plan(List.of(), three, new FrontSettings(NodeRegion.US, 2, 20))).hasSize(2);

        FrontSettings perUser50 = new FrontSettings(NodeRegion.US, 3, 50);
        List<Assignment> sixPrimaries = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new Assignment(100 + i, List.of(new Slot(10, 1))))
                .toList();
        assertThat(FrontAllocationPlanner.plan(sixPrimaries, List.of(new Candidate(1, 10, 300)), perUser50)).isEmpty();
    }

    @Test
    @DisplayName("planAll：按给定用户顺序从零重排，结果只取决于输入；三家 300M、6 人时主用各 2 人")
    void planAllIsDeterministicAndBalanced() {
        List<Candidate> three = List.of(new Candidate(1, 10, 300), new Candidate(2, 20, 300), new Candidate(3, 30, 300));
        List<Long> users = List.of(1L, 2L, 3L, 4L, 5L, 6L);

        LinkedHashMap<Long, List<Slot>> first = FrontAllocationPlanner.planAll(users, three, SETTINGS);
        LinkedHashMap<Long, List<Slot>> second = FrontAllocationPlanner.planAll(users, three, SETTINGS);

        assertThat(first).isEqualTo(second);
        assertThat(first.keySet()).containsExactlyElementsOf(users);
        Map<Long, Long> primaries = first.values().stream()
                .collect(Collectors.groupingBy(slots -> slots.get(0).airportSubscriptionId(), Collectors.counting()));
        assertThat(primaries).containsOnly(Map.entry(1L, 2L), Map.entry(2L, 2L), Map.entry(3L, 2L));
        first.values().forEach(slots -> assertThat(slots).hasSize(3));
    }

    @Test
    @DisplayName("planAll：主用名额不够时后面的用户得到空列表（调用方据此中止）")
    void planAllLeavesEmptyWhenCapacityRunsOut() {
        List<Candidate> tiny = List.of(new Candidate(1, 10, 40));   // 容量 2
        LinkedHashMap<Long, List<Slot>> planned = FrontAllocationPlanner.planAll(List.of(1L, 2L, 3L), tiny, SETTINGS);
        assertThat(planned.get(1L)).hasSize(1);
        assertThat(planned.get(2L)).hasSize(1);
        assertThat(planned.get(3L)).isEmpty();
    }

    @Test
    @DisplayName("非主用机场：主用位跳过它（哪怕它负载最低），备用位仍可选它")
    void nonPrimaryAirportOnlyServesAsBackup() {
        // A 是唯一的主用机场且已有 10 人主用；B、C 空着但不是主用机场
        List<Assignment> others = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> user(id, a1())).toList();
        List<Candidate> candidates = List.of(
                new Candidate(A1, A, 300, true), new Candidate(B1, B, 300, false), new Candidate(C1, C, 300, false));

        List<Slot> planned = FrontAllocationPlanner.plan(others, candidates, SETTINGS);

        assertThat(planned).hasSize(3);
        assertThat(planned.get(0)).isEqualTo(a1());
        assertThat(planned.subList(1, 3)).containsExactlyInAnyOrder(b1(), c1());
    }

    @Test
    @DisplayName("只有非主用机场时选不出主用，返回空列表；其主用容量记 0")
    void noPrimaryAirportYieldsEmpty() {
        Candidate backupOnly = new Candidate(A1, A, 300, false);
        assertThat(backupOnly.primaryCapacity(20)).isZero();
        assertThat(FrontAllocationPlanner.plan(List.of(), List.of(backupOnly), SETTINGS)).isEmpty();
    }
}
