package club.boyuan.official.infra.ratelimit;

import club.boyuan.official.common.exception.RateLimitExceededException;
import club.boyuan.official.infra.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Redis 滑动窗口限流。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final String KEY_PREFIX = "rate:gateway:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties rateLimitProperties;

    private DefaultRedisScript<Long> slidingWindowScript;

    @PostConstruct
    void init() {
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setResultType(Long.class);
        slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("redis/lua/rate_limit_sliding_window.lua")));
    }

    /**
     * @return true 允许通过；false 触发限流
     */
    public boolean tryAcquire(String ruleName, String identity, int limit, int windowSeconds) {
        String key = KEY_PREFIX + ruleName + ":" + identity;
        long nowMs = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Long allowed = stringRedisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(limit));
        boolean pass = allowed != null && allowed == 1L;
        if (!pass) {
            log.warn("触发限流 rule={}, identity={}, limit={}/{}s", ruleName, identity, limit, windowSeconds);
        }
        return pass;
    }

    /**
     * 按业务身份（邮箱等）限流，超限直接抛 429。
     * <p>
     * 这条路径专给 Controller 用：拦截器跑在读请求体之前，拿不到邮箱，
     * 所以邮箱维度只能等参数解析完再查。规则缺失时放行——宁可不限，
     * 也不要因为漏配一行 yml 把注册入口整个关掉。
     *
     * @param ruleName application.yml 中 rate-limit.identity-rules 下的规则名
     * @param identity 业务身份，调用方需自带前缀（如 "email:xxx@stu.ecnu.edu.cn"）
     */
    public void acquireIdentityOrThrow(String ruleName, String identity) {
        if (!rateLimitProperties.isEnabled()) {
            return;
        }
        var rule = rateLimitProperties.getIdentityRules().get(ruleName);
        if (rule == null) {
            return;
        }
        if (!tryAcquire(ruleName, identity, rule.getLimit(), rule.getWindowSeconds())) {
            throw new RateLimitExceededException(rule.getWindowSeconds());
        }
    }
}
