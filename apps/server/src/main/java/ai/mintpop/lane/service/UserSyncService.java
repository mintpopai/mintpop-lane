package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 登录时的建档与资料同步：库里无此 sub 即建档（只有身份，无资源无订阅），有则刷新 email/name。
 * 这是唯一的建档入口——管理端不提供「新建用户」。
 */
@Service
public class UserSyncService {

    private final UserRepository userRepository;

    public UserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto syncOnLogin(String subject, String email, String name) {
        String safeName = name == null || name.isBlank() ? email.split("@")[0] : name;

        return userRepository.findBySubject(subject)
                .map(user -> 刷新资料(user, email, safeName))
                .orElseGet(() -> 建档(subject, email, safeName));
    }

    private UserDto 刷新资料(UserDto user, String email, String name) {
        if (Objects.equals(user.getEmail(), email) && Objects.equals(user.getName(), name)) {
            return user;
        }
        user.setEmail(email);
        user.setName(name);
        // update 会整行回写，user 来自 findBySubject 的完整 DTO，角色/处置态/节点原样保留
        userRepository.update(user);
        return user;
    }

    private UserDto 建档(String subject, String email, String name) {
        UserDto user = new UserDto();
        user.setSubject(subject);
        user.setEmail(email);
        user.setName(name);
        // role=MEMBER、status=ACTIVE 由 DTO 默认值提供；不分配任何节点与订阅
        try {
            user.setId(userRepository.create(user));
            return user;
        } catch (DuplicateKeyException e) {
            // 同一账号并发首登撞唯一索引：另一次已建好，读回来用
            return userRepository.findBySubject(subject).orElseThrow();
        }
    }
}
