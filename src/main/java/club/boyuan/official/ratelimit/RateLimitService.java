package club.boyuan.official.ratelimit;

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
}
