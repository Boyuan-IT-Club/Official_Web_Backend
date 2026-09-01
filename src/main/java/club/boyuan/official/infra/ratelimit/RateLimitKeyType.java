package club.boyuan.official.infra.ratelimit;

/**
 * 限流维度:按 IP、按登录用户、按 TCP 对端地址。
 * REMOTE_ADDR 使用 request.getRemoteAddr(),不信任 X-Forwarded-For(防伪造绕过),
 * 适用于零认证公开写端点;代价是反代后所有客户端共用一个桶,需配合较高 limit。
 */
public enum RateLimitKeyType {
    IP,
    USER,
    REMOTE_ADDR
}
