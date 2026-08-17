package com.mintpop.server.service;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.entity.Employee;
import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.enumeration.EmployeeStatus;
import com.mintpop.server.exception.BizException;
import com.mintpop.server.repository.EmployeeRepository;
import com.mintpop.server.response.HeartbeatResponse;
import com.mintpop.server.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final EmployeeRepository employeeRepository;

    public LinkServiceImpl(LinkProperties linkProperties, EmployeeRepository employeeRepository) {
        this.linkProperties = linkProperties;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public LinkConfigResponse resolveLink(String subject) {
        Employee employee = employeeRepository.findBySubject(subject)
                .orElseThrow(() -> new BizException(BizCodeEnum.ACCOUNT_NOT_ENROLLED));

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.LINK_REVOKED);
        }

        // 没有期望出口，客户端就无法做出口校验，等于放弃 fail-closed 的第二道闸
        if (employee.getExpectedEgressIps() == null || employee.getExpectedEgressIps().isEmpty()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        if (employee.getClaudeCredential() == null || employee.getClaudeCredential().isBlank()) {
            throw new BizException(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
        }

        return new LinkConfigResponse(
                linkProperties.getFront(),
                employee.getLand(),
                employee.getExpectedEgressIps(),
                employee.getClaudeCredential(),
                linkProperties.getTtlSeconds()
        );
    }

    @Override
    public HeartbeatResponse heartbeat(String subject) {
        // 从绑定表中消失等价于权限被收回，按吊销处理让客户端断链
        EmployeeStatus status = employeeRepository.findBySubject(subject)
                .map(Employee::getStatus)
                .orElse(EmployeeStatus.REVOKED);

        return new HeartbeatResponse(status);
    }
}
