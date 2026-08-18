package com.mintpop.server.repository;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 从配置文件读取用户绑定表的实现。 */
@Repository
public class PropertiesUserRepository implements UserRepository {

    private final LinkProperties linkProperties;

    public PropertiesUserRepository(LinkProperties linkProperties) {
        this.linkProperties = linkProperties;
    }

    @Override
    public Optional<User> findBySubject(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        return linkProperties.getUsers().stream()
                .filter(u -> subject.equals(u.getSubject()))
                .findFirst();
    }
}
