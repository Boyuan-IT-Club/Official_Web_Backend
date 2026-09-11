package club.boyuan.official.common.exception;

import lombok.Getter;

/**
 * 触发限流。相比普通 BusinessException 多带一个「多久之后可以重试」，
 * 由 GlobalExceptionHandler 写进 Retry-After 响应头。
 * <p>
 * 之所以要把这个秒数一路带到响应里：前端拿不到它就只能干等或者盲目重试。
 * 2026-09-11 线上就是这样——注册限流是 3 次/小时，某个同学的页面在
 * 11 分钟里重试了 211 次，用户侧只看到一个「操作失败」。
 */
@Getter
public class RateLimitExceededException extends BusinessException {

    /** 建议的重试间隔（秒），即限流窗口长度 */
    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super(BusinessExceptionEnum.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
