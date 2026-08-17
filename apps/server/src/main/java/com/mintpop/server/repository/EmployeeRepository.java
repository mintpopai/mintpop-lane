package com.mintpop.server.repository;

import com.mintpop.server.entity.Employee;

import java.util.Optional;

/**
 * 员工绑定表的读取口。
 * 定成接口是为了让实现可替换：第一版读配置文件，后续换数据库时上层无感。
 */
@FunctionalInterface
public interface EmployeeRepository {

    Optional<Employee> findBySubject(String subject);
}
