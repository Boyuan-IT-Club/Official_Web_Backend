package club.boyuan.official.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 面试预约秒杀模式配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "booking.seckill")
public class InterviewBookingSeckillProperties {

    /** 是否启用秒杀链路（Lua 预扣 + MQ 异步落库） */
    private boolean enabled = true;

    /** 用户周期锁 TTL（秒），防止同用户并发重复提交 */
    private int userLockTtlSeconds = 600;

    /** 预约请求状态在 Redis 中的 TTL（小时） */
    private int requestStatusTtlHours = 24;
}
