package club.boyuan.official.ratelimit;

/**
 * 限流维度：按 IP 或按登录用户。
 */
public enum RateLimitKeyType {
    IP,
    USER
}
