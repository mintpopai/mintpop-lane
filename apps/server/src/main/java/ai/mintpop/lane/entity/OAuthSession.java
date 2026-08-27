package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/**
 * oauth_session 表映射。授权会话只在「生成授权链接」到「兑换凭证」之间存活，
 * 兑换成功或超时即删除。
 */
@Data
@TableName("oauth_session")
public class OAuthSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long subscriptionId;

    /** PKCE code_verifier 的密文。排除出 toString，避免随日志外泄 */
    @ToString.Exclude
    private String codeVerifierCipher;

    private String state;

    private String scope;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    private Instant expiresAt;
}
