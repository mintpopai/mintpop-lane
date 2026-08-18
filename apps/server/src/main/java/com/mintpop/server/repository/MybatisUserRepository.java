package com.mintpop.server.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.server.converter.UserConverter;
import com.mintpop.server.dto.PageResult;
import com.mintpop.server.dto.UserDto;
import com.mintpop.server.entity.User;
import com.mintpop.server.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 用户的 MySQL 实现。 */
@Repository
public class MybatisUserRepository implements UserRepository {

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
    public PageResult<UserDto> search(String keyword, long pageNo, long pageSize) {
        var query = Wrappers.<User>lambdaQuery().orderByAsc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(User::getName, keyword).or().like(User::getSubject, keyword));
        }
        Page<User> page = mapper.selectPage(Page.of(pageNo, pageSize), query);
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
                .set(User::getName, entity.getName())
                .set(User::getRole, entity.getRole())
                .set(User::getStatus, entity.getStatus())
                .set(User::getFrontNodeId, entity.getFrontNodeId())
                .set(User::getLandNodeId, entity.getLandNodeId())
                .set(User::getClaudeCredentialCipher, entity.getClaudeCredentialCipher()));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsBySubject(String subject) {
        return mapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getSubject, subject)) > 0;
    }

    @Override
    public boolean existsByFrontNodeId(Long nodeId) {
        return mapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getFrontNodeId, nodeId)) > 0;
    }
}
