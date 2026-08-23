package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 登录时的建档与资料同步：库里无此 sub 即建档（只有身份，无资源无订阅），有则刷新 email。
 * 这是唯一的建档入口——管理端不提供「新建用户」。
 * <p>
 * 系统里没有「用户名」这一概念：邮箱就是用户的唯一业务标识（app_user 上有唯一索引），
 * subject 只承担「按 Logto 账号找到档案」这一件事。
 */
@Service
public class UserSyncService {

    private final UserRepository userRepository;

    public UserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto syncOnLogin(String subject, String email) {
        return userRepository.findBySubject(subject)
                .map(user -> refreshProfile(user, email))
                .orElseGet(() -> createProfile(subject, email));
    }

    private UserDto refreshProfile(UserDto user, String email) {
        if (Objects.equals(user.getEmail(), email)) {
            return user;
        }
        String previousEmail = user.getEmail();
        user.setEmail(email);
        try {
            // update 会整行回写，user 来自 findBySubject 的完整 DTO，角色/处置态/节点原样保留
            userRepository.update(user);
        } catch (DuplicateKeyException e) {
            // 在 Logto 里把邮箱改成了别人已占用的那个：邮箱是唯一标识，不能两个档案共用
            user.setEmail(previousEmail);
            throw new BizException(BizCodeEnum.EMAIL_ALREADY_BOUND);
        }
        return user;
    }

    private UserDto createProfile(String subject, String email) {
        UserDto user = new UserDto();
        user.setSubject(subject);
        user.setEmail(email);
        // role=MEMBER、status=ACTIVE 由 DTO 默认值提供；不分配任何节点与订阅
        try {
            user.setId(userRepository.create(user));
            return user;
        } catch (DuplicateKeyException e) {
            // 撞唯一索引有两种可能，按能不能读回本人的档案区分：
            // 撞 subject = 同一账号并发首登，另一次已建好，读回来用；
            // 撞 email = 这个邮箱已绑在别的 Logto 账号上，读不回来，必须报错而不是静默放行
            return userRepository.findBySubject(subject)
                    .orElseThrow(() -> new BizException(BizCodeEnum.EMAIL_ALREADY_BOUND));
        }
    }
}
