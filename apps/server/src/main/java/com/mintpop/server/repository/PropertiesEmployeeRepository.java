package com.mintpop.server.repository;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.entity.Employee;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 从配置文件读取员工绑定表的实现。 */
@Repository
public class PropertiesEmployeeRepository implements EmployeeRepository {

    private final LinkProperties linkProperties;

    public PropertiesEmployeeRepository(LinkProperties linkProperties) {
        this.linkProperties = linkProperties;
    }

    @Override
    public Optional<Employee> findBySubject(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        return linkProperties.getEmployees().stream()
                .filter(e -> subject.equals(e.getSubject()))
                .findFirst();
    }
}
