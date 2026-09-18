package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用 JDK 自带的 JNDI dns: provider 查 CNAME，不引第三方 DNS 库、不走 HTTP。
 *
 * 刻意不缓存：调用点只有「导入/刷新订阅」与「尽调」，频率以天计，缓存只会让结果陈旧。
 * 本类不加 @Component——构造依赖一个 CnameLookup，由 DnsConfig 的 @Bean 装配（与 EgressIpVerifier 同一模式）。
 */
@Slf4j
public class JndiFailureDomainResolver implements FailureDomainResolver {

    /** 单次 CNAME 查询，抽成接口便于测试替换 */
    @FunctionalInterface
    public interface CnameLookup {
        /** @return 该 host 的 CNAME 目标；没有 CNAME 记录返回 null */
        String cnameOf(String host) throws Exception;
    }

    /** CNAME 链最多跟这么多跳，超过即判成环 */
    private static final int MAX_HOPS = 10;

    private final CnameLookup lookup;

    public JndiFailureDomainResolver(CnameLookup lookup) {
        this.lookup = lookup;
    }

    /** 生产用查询实现：JNDI dns: provider */
    public static CnameLookup jndiLookup() {
        return host -> {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(host, new String[]{"CNAME"});
                Attribute cname = attrs.get("CNAME");
                return cname == null ? null : stripTrailingDot(String.valueOf(cname.get()));
            } finally {
                ctx.close();
            }
        };
    }

    @Override
    public String resolve(String host) {
        Set<String> seen = new LinkedHashSet<>();
        String current = host;
        try {
            for (int hop = 0; hop < MAX_HOPS; hop++) {
                if (!seen.add(current)) {
                    log.warn("CNAME 链成环，故障域按未知处理 host={}", host);
                    return null;
                }
                String next = lookup.cnameOf(current);
                if (next == null || next.isBlank()) {
                    return current;
                }
                current = stripTrailingDot(next);
            }
        } catch (Exception e) {
            // 与 EgressCheckService 对探测失败的处理一致：网络抖动不等于事实变化，不写库
            log.warn("故障域解析失败，本次按未知处理 host={} 原因={}", host, e.getClass().getSimpleName());
            return null;
        }
        log.warn("CNAME 链超过 {} 跳，故障域按未知处理 host={}", MAX_HOPS, host);
        return null;
    }

    private static String stripTrailingDot(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }
}
