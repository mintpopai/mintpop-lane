package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.request.DeviceBindRequest;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;
import ai.mintpop.lane.util.RebindRequestNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户侧的设备绑定。
 *
 * <p>竞态一律靠条件 UPDATE 挡，不做查-判-写：两台设备同时对同一份未绑定订阅点绑定时，
 * 「先查发现未绑定、再写」的中间窗口正好够第二台也判定成未绑定，于是后写的那台把先写的顶掉。
 */
@Slf4j
@Service
public class DeviceBindingServiceImpl implements DeviceBindingService {

    /** 同一秒撞上申请号的重试次数。撞号概率是百万分之一量级，三次足够 */
    private static final int REQUEST_NO_RETRIES = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final DeviceRebindRequestRepository rebindRequestRepository;
    private final Clock clock;

    public DeviceBindingServiceImpl(SubscriptionRepository subscriptionRepository,
                                    UserDeviceRepository userDeviceRepository,
                                    DeviceRebindRequestRepository rebindRequestRepository,
                                    Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.rebindRequestRepository = rebindRequestRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void bind(Long userId, Long subscriptionId, String deviceId, DeviceBindRequest body) {
        SubscriptionDto subscription = ownedSubscription(userId, subscriptionId);
        Instant now = clock.instant();
        UserDevice device = userDeviceRepository.upsert(
                userId, deviceId, body.getName(), body.getOs(),
                body.getModel() == null ? "" : body.getModel(), now);

        // 已绑在本机：幂等成功。用户重装客户端、或两个窗口各点一次，都不该报错
        if (Objects.equals(subscription.getBoundDeviceId(), device.getId())) {
            return;
        }
        if (subscription.getBoundDeviceId() != null) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_BOUND_ELSEWHERE);
        }
        if (!subscriptionRepository.bindDeviceIfUnbound(subscriptionId, device.getId(), now)) {
            // 条件更新没生效 = 这一瞬间被另一台设备抢先绑走。如实报，绝不假装成功——
            // 假装成功的话客户端会接着去开会话，而凭据根本不会下发给它
            throw new BizException(BizCodeEnum.SUBSCRIPTION_BOUND_ELSEWHERE);
        }
        log.info("订阅绑定设备，subscriptionId={}，deviceRowId={}", subscriptionId, device.getId());
    }

    @Override
    @Transactional
    public Long requestRebind(Long userId, Long subscriptionId, String deviceId,
                              DeviceRebindCreateRequest body) {
        SubscriptionDto subscription = ownedSubscription(userId, subscriptionId);
        // 「作废旧 PENDING + 建新申请」这一对必须按订阅串行化：它不是单条条件 UPDATE 能表达的，
        // MySQL 也没有部分唯一索引可以在库层兜底（V17 因此只建了普通索引）。不锁的话，两台新机器
        // 同时提申请、或用户双击一次「提交申请」，两边都作废 0 条、都插入成功，同一份订阅留下两条
        // PENDING——管理员看到两条一模一样的待办，同意了过期的那条就把订阅绑到用户已经放弃的机器上。
        // 本方法是 @Transactional，锁因此一直持有到提交，这正是它生效的前提；锁在事务外取等于没取
        subscriptionRepository.findByIdForUpdate(subscriptionId);
        Instant now = clock.instant();
        UserDevice device = userDeviceRepository.upsert(
                userId, deviceId, body.getName(), body.getOs(),
                body.getModel() == null ? "" : body.getModel(), now);

        // 没绑在别处就没什么可申请的：未绑定该直接绑，已绑本机本来就能用
        if (subscription.getBoundDeviceId() == null
                || Objects.equals(subscription.getBoundDeviceId(), device.getId())) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_NOT_BOUND_ELSEWHERE);
        }

        // 同一份订阅同时只留一条 PENDING：用户在第三台机器上又提一次时，
        // 第二台那条已经没有意义了，作废掉而不是让管理员在两条里挑。
        // upsert/supersedePending/create 三步必须在同一个事务里：撞键重试三次仍失败会
        // 让异常逸出本方法，若无事务包裹，supersedePending 已提交的作废会永久生效，
        // 而新申请没建出来——用户之前那条待处理申请无声消失，管理员也少了一条待办
        rebindRequestRepository.supersedePending(subscriptionId);

        DeviceRebindRequest request = new DeviceRebindRequest();
        request.setSubscriptionId(subscriptionId);
        request.setUserId(userId);
        request.setFromDeviceId(subscription.getBoundDeviceId());
        request.setToDeviceId(device.getId());
        request.setReason(body.getReason());
        request.setStatus(RebindRequestStatus.PENDING);
        return createWithUniqueRequestNo(request, now);
    }

    /** 申请号撞唯一键就换一个重试，与分配号、订单号同一套路 */
    private Long createWithUniqueRequestNo(DeviceRebindRequest request, Instant now) {
        DuplicateKeyException last = null;
        for (int i = 0; i < REQUEST_NO_RETRIES; i++) {
            request.setRequestNo(RebindRequestNo.generate(now));
            try {
                return rebindRequestRepository.create(request);
            } catch (DuplicateKeyException e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * 取这个用户自己的、且在期的订阅。别人的一律当作不存在——不泄露它的存在。
     *
     * <p>在期是绑定与换机共同的前置条件：不在期的席位本来就不下发凭据，
     * 让它绑定等于把「一份订阅只能在一台设备上用」这枚额度烧在一个根本用不了的席位上；
     * 让它提换机申请，则是为一个谁都用不了的席位去打扰管理员（还会推一张飞书卡片）。
     *
     * <p>两种不在期分开报：待开通（管理员还没填起期）与已过期，用户要做的事完全不同——
     * 前者去催管理员，后者去续期。用同一句话会把人引到错误的方向
     */
    private SubscriptionDto ownedSubscription(Long userId, Long subscriptionId) {
        SubscriptionDto subscription = subscriptionRepository.findById(subscriptionId)
                .filter(s -> Objects.equals(s.getUserId(), userId))
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        Instant now = clock.instant();
        if (subscription.isActiveAt(now)) {
            return subscription;
        }
        // 已过止期是「过期了，去续期」；起期没填或还没到都是「尚未开通，去催管理员」。
        // 合成一句会把两拨用户引到彼此的错误方向上
        if (subscription.getEndsAt() != null && !now.isBefore(subscription.getEndsAt())) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_EXPIRED);
        }
        throw new BizException(BizCodeEnum.SUBSCRIPTION_NOT_ACTIVATED);
    }
}
