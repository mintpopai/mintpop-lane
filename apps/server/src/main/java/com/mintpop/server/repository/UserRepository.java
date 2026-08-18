package com.mintpop.server.repository;

import com.mintpop.server.entity.User;

import java.util.Optional;

/**
 * 用户绑定表的读取口。
 * 定成接口是为了让实现可替换：第一版读配置文件，Task 5 起换成数据库实现。
 */
@FunctionalInterface
public interface UserRepository {

    Optional<User> findBySubject(String subject);
}
