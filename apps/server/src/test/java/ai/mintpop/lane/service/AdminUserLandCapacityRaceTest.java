package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.UserSaveRequest;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 落地容量校验的并发竞态：重存同一节点时不能信任「加节点行锁之前」读到的用户快照。
 * 用 spy 在主流程「已读到旧快照、尚未拿到 FOR UPDATE 锁」的间隙里注入并发操作，
 * 确定性地复现窗口，不靠线程调度碰运气。
 */
class AdminUserLandCapacityRaceTest extends MysqlTestBase {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** spy 用于在锁定读之前插入并发事务；未打桩时行为与真实 bean 一致 */
    @MockitoSpyBean
    private ProxyNodeRepository nodeRepository;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
    }

    private UserSaveRequest saveRequest(Long frontNodeId, Long landNodeId) {
        UserSaveRequest request = new UserSaveRequest();
        request.setStatus(UserStatus.ACTIVE);
        request.setFrontNodeId(frontNodeId);
        request.setLandNodeId(landNodeId);
        return request;
    }

    /** 在独立线程里跑（即独立事务），并等它提交完成 */
    private void runInSeparateTransaction(Runnable action) {
        Thread thread = new Thread(action);
        thread.setUncaughtExceptionHandler((t, e) -> {
            throw new IllegalStateException("并发事务执行失败", e);
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("重存同一节点的间隙里被解绑且名额被抢走时，报 410016 而不是凭旧快照超卖")
    void staleSnapshotMustNotBypassCapacityCheck() {
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land = fixtures.createLandNode("LAND-1", "203.0.113.20", 1);
        Long userU = fixtures.createUser("logto-u", front, land);
        Long userW = fixtures.createUser("logto-w", front, null);

        // 主流程（重存 U，落地仍指向 LAND-1）第一次锁定读节点行之前：
        // 另一个管理员先把 U 解绑，再把最后一个名额分给 W，两个事务都已提交
        AtomicBoolean firstLockingRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstLockingRead.compareAndSet(true, false)) {
                runInSeparateTransaction(() -> {
                    adminUserService.update(userU, saveRequest(front, null));
                    adminUserService.update(userW, saveRequest(front, land));
                });
            }
            return invocation.callRealMethod();
        }).when(nodeRepository).findByIdForUpdate(anyLong());

        assertThatThrownBy(() -> adminUserService.update(userU, saveRequest(front, land)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getBizCode()).isEqualTo(BizCodeEnum.LAND_NODE_FULL));
        assertThat(userRepository.countByLandNodeId(land)).isEqualTo(1);
    }
}
