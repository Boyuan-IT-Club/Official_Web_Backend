package club.boyuan.official.infra.ratelimit;

import lombok.Data;

/**
 * 单条限流规则（对应 application.yml 配置）。
 */
@Data
public class RateLimitRuleConfig {

    /** 规则名，仅用于日志 */
    private String name;

    /** Ant 风格路径，如 /api/auth/login */
    private String pattern;

    /** HTTP 方法，默认 POST */
    private String method = "POST";

    /** 窗口内最大请求数 */
    private int limit = 10;

    /** 窗口长度（秒） */
    private int windowSeconds = 60;

    /** 限流 key 维度 */
    private RateLimitKeyType keyType = RateLimitKeyType.IP;
}
