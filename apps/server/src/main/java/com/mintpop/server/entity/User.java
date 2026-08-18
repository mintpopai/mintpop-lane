package com.mintpop.server.entity;

import com.mintpop.server.enumeration.UserStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户绑定记录：一名用户 →（专属落地出口, Claude 席位凭据）。
 * 一人一 IP 一席位，风控排查与吊销都能精确到人。
 */
@Data
public class User {

    /** 该用户在 Logto 中的 user id，即 JWT 的 sub */
    private String subject;

    /** 姓名，仅用于服务端日志排查 */
    private String name;

    /** 当前状态 */
    private UserStatus status = UserStatus.ACTIVE;

    /** 该用户专属落地出口的 IP 集合，供客户端做出口校验 */
    private List<String> expectedEgressIps = List.of();

    /**
     * 第二跳：该用户专属的后置落地代理，原样透传的 mihomo 节点配置。
     * 协议字段千变万化，不做强类型化。
     */
    private Map<String, Object> land = Map.of();

    /** 该用户的 Claude 席位长效凭据 */
    private String claudeCredential;
}
