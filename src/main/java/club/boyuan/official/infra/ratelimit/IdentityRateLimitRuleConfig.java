package club.boyuan.official.infra.ratelimit;

import lombok.Data;

/**
 * 「按业务身份」的限流规则（对应 application.yml 的 rate-limit.identity-rules）。
 * <p>
 * 和 {@link RateLimitRuleConfig} 的区别是不带路径：这类规则的 key 取自请求体里的
 * 业务字段（目前是邮箱），拦截器在读请求体之前跑，拿不到，只能由 Controller 主动调用。
 */
@Data
public class IdentityRateLimitRuleConfig {

    /** 窗口内最大请求数 */
    private int limit = 5;

    /** 窗口长度（秒） */
    private int windowSeconds = 3600;
}
