package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ai.mintpop.lane.converter.UserConverter;
import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.User;
import ai.mintpop.lane.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 用户的 MySQL 实现。 */
@Repository
public class MybatisUserRepository implements UserRepository {

    /** 分页大小的默认值与上限，钳制口径与管理端接口的默认值保持一致 */
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;

    private final UserMapper mapper;
    private final UserConverter converter;

    public MybatisUserRepository(UserMapper mapper, UserConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<UserDto> findBySubject(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        mapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getSubject, subject)))
                .map(converter::toDto);
    }

    @Override
    public Optional<UserDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(converter::toDto);
    }

    @Override
    public Optional<UserDto> findByLandNodeId(Long landNodeId) {
        if (landNodeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        mapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getLandNodeId, landNodeId)))
                .map(converter::toDto);
    }

    @Override
    public PageResult<UserDto> search(String keyword, Boolean hasActiveSubscription, long pageNo, long pageSize) {
        // 钳制分页参数：MyBatis-Plus 在 size 为负且未设 maxLimit 时不会拼 LIMIT，
        // 外部传入的负数会退化成全表返回、逐行解密，必须在这里挡住
        long safePageNo = pageNo < 1 ? 1 : pageNo;
        long safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        var query = Wrappers.<User>lambdaQuery().orderByAsc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            // email 是管理员在列表里唯一认得出的标识（姓名可能重复、subject 是 Logto 内部 id），必须能搜
            query.and(w -> w.like(User::getName, keyword)
                    .or().like(User::getSubject, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (Boolean.TRUE.equals(hasActiveSubscription)) {
            query.exists("SELECT 1 FROM subscription s WHERE s.user_id = app_user.id"
                    + " AND s.starts_at <= UTC_TIMESTAMP() AND s.ends_at > UTC_TIMESTAMP()");
        } else if (Boolean.FALSE.equals(hasActiveSubscription)) {
            query.notExists("SELECT 1 FROM subscription s WHERE s.user_id = app_user.id"
                    + " AND s.starts_at <= UTC_TIMESTAMP() AND s.ends_at > UTC_TIMESTAMP()");
        }
        Page<User> page = mapper.selectPage(Page.of(safePageNo, safePageSize), query);
        return new PageResult<>(
                page.getRecords().stream().map(converter::toDto).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    @Override
    public Long create(UserDto user) {
        User entity = converter.toEntity(user);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(UserDto user) {
        User entity = converter.toEntity(user);
        // 逐列 set，而不是 updateById：后者默认跳过 null 字段，
        // 会让「取消落地分配」这类把值改成 null 的操作静默失效
        mapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getSubject, entity.getSubject())
                .set(User::getEmail, entity.getEmail())
                .set(User::getName, entity.getName())
                .set(User::getRole, entity.getRole())
                .set(User::getStatus, entity.getStatus())
                .set(User::getFrontNodeId, entity.getFrontNodeId())
                .set(User::getLandNodeId, entity.getLandNodeId()));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByFrontNodeId(Long nodeId) {
        return mapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getFrontNodeId, nodeId)) > 0;
    }
}
