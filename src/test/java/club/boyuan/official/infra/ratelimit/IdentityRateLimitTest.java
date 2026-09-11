package club.boyuan.official.infra.ratelimit;

import club.boyuan.official.common.exception.RateLimitExceededException;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.GlobalExceptionHandler;
import club.boyuan.official.infra.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 邮箱维度限流。
 * <p>
 * 背景：2026-09-11 线上注册限流是「3 次/小时、按 IP」，校园 NAT 下一栋楼共用
 * 一个出口，某位同学两次 409 加一次成功就把名额耗尽，之后 11 分钟里打出 211 次
 * 429。IP 维度因此放宽，防刷改由邮箱维度承担——这组断言锁住新维度真的生效，
 * 且超限时带得出 Retry-After（前端靠它停止重试）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityRateLimitTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @InjectMocks
    private RateLimitService rateLimitService;

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        // init() 平时由 @PostConstruct 调，这里手动准备脚本对象
        ReflectionTestUtils.invokeMethod(rateLimitService, "init");
        when(rateLimitProperties.isEnabled()).thenReturn(true);
    }

    private void givenRule(String name, int limit, int windowSeconds) {
        IdentityRateLimitRuleConfig rule = new IdentityRateLimitRuleConfig();
        rule.setLimit(limit);
        rule.setWindowSeconds(windowSeconds);
        when(rateLimitProperties.getIdentityRules()).thenReturn(java.util.Map.of(name, rule));
    }

    @Test
    @DisplayName("未配置的规则直接放行——漏配一行 yml 不该把注册入口整个关掉")
    void missingRulePasses() {
        when(rateLimitProperties.getIdentityRules()).thenReturn(java.util.Map.of());
        assertDoesNotThrow(() ->
                rateLimitService.acquireIdentityOrThrow("auth-register-email", "email:a@stu.ecnu.edu.cn"));
    }

    @Test
    @DisplayName("限流总开关关掉时不拦")
    void disabledPasses() {
        when(rateLimitProperties.isEnabled()).thenReturn(false);
        assertDoesNotThrow(() ->
                rateLimitService.acquireIdentityOrThrow("auth-register-email", "email:a@stu.ecnu.edu.cn"));
    }

    @Test
    @DisplayName("窗口内未超限放行")
    void withinLimitPasses() {
        givenRule("auth-register-email", 5, 3600);
        when(stringRedisTemplate.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        assertDoesNotThrow(() ->
                rateLimitService.acquireIdentityOrThrow("auth-register-email", "email:a@stu.ecnu.edu.cn"));
    }

    @Test
    @DisplayName("超限抛 429，并把窗口长度作为 Retry-After 带出去")
    void overLimitThrowsWithRetryAfter() {
        givenRule("auth-register-email", 5, 3600);
        when(stringRedisTemplate.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                rateLimitService.acquireIdentityOrThrow("auth-register-email", "email:a@stu.ecnu.edu.cn"));

        assertEquals(3600, ex.getRetryAfterSeconds(), "Retry-After 应当等于窗口长度");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus(), "限流必须是 429，不能被压成 400");
    }

    @Test
    @DisplayName("GlobalExceptionHandler 把 Retry-After 写进响应头")
    void handlerWritesRetryAfterHeader() {
        ResponseEntity<ResponseMessage<?>> response =
                exceptionHandler.handleRateLimitExceeded(new RateLimitExceededException(300));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("300", response.getHeaders().getFirst("Retry-After"),
                "没有这个头，前端只能盲目重试");
        assertEquals(4291, response.getBody().getCode());
    }
}
