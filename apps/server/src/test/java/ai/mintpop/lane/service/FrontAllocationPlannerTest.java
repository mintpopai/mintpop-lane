package ai.mintpop.lane.service;

import ai.mintpop.lane.service.FrontAllocationPlanner.Assignment;
import ai.mintpop.lane.service.FrontAllocationPlanner.Candidate;
import ai.mintpop.lane.service.FrontAllocationPlanner.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("第一跳分配算法：场景负载 + 逐位贪心（spec 第四节）")
class FrontAllocationPlannerTest {

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
        assertThat(FrontAllocationPlanner.plan(specExistingUsers(), threeAirports300()))
                .containsExactly(b1(), c1(), a1());
    }

    @Test
    @DisplayName("主用名额全满时返回空列表（300M 容量 15，已有 15 个主用）")
    void emptyWhenAllPrimaryFull() {
        List<Assignment> full = java.util.stream.LongStream.rangeClosed(1, 15)
                .mapToObj(id -> user(id, a1())).toList();
        assertThat(FrontAllocationPlanner.plan(full, List.of(new Candidate(A1, A, 300)))).isEmpty();
    }

    @Test
    @DisplayName("主用只从未满的订阅里选；满额订阅仍可当备用")
    void fullSubscriptionStillUsableAsBackup() {
        List<Assignment> aFull = java.util.stream.LongStream.rangeClosed(1, 15)
                .mapToObj(id -> user(id, a1())).toList();
        List<Slot> planned = FrontAllocationPlanner.plan(aFull,
                List.of(new Candidate(A1, A, 300), new Candidate(B1, B, 300)));
        assertThat(planned).containsExactly(b1(), a1());
    }

    @Test
    @DisplayName("可用机场少于 3 家时列表变短，不报错")
    void shortListWhenFewerAirportsThanMax() {
        assertThat(FrontAllocationPlanner.plan(List.of(), List.of(new Candidate(A1, A, 300))))
                .containsExactly(a1());
    }

    @Test
    @DisplayName("同一家机场在列表里最多出现一次：同机场的第二个订阅不会被选作备用")
    void neverRepeatsAnAirport() {
        List<Slot> planned = FrontAllocationPlanner.plan(List.of(),
                List.of(new Candidate(A1, A, 300), new Candidate(12, A, 300)));
        assertThat(planned).hasSize(1);
    }

    @Test
    @DisplayName("按「人数/带宽」比较：大带宽订阅按比例多接人")
    void comparesByLoadPerBandwidth() {
        // A1 600M 已有 3 人（(3+1)/600），B1 300M 已有 2 人（(2+1)/300）：A1 更空
        List<Assignment> users = List.of(user(1, a1()), user(2, a1()), user(3, a1()),
                user(4, b1()), user(5, b1()));
        List<Slot> planned = FrontAllocationPlanner.plan(users,
                List.of(new Candidate(A1, A, 600), new Candidate(B1, B, 300)));
        assertThat(planned.get(0)).isEqualTo(a1());
    }

    @Test
    @DisplayName("平手按订阅 id 小的优先，结果确定可复现")
    void tieBreaksBySubscriptionId() {
        assertThat(FrontAllocationPlanner.plan(List.of(), threeAirports300()).get(0)).isEqualTo(a1());
    }

    @Test
    @DisplayName("主用容量 = 带宽 / 20 向下取整")
    void primaryCapacityFloors() {
        assertThat(new Candidate(A1, A, 110).primaryCapacity()).isEqualTo(5);
        assertThat(FrontAllocationPlanner.primaryCapacity(300)).isEqualTo(15);
    }
}
