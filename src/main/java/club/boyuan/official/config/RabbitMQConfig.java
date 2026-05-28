package club.boyuan.official.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/**
 * RabbitMQ 配置：声明邮箱验证码队列，并统一使用 JSON 序列化消息体。
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    /**
     * 邮箱验证码发送队列（持久化，Broker 重启后队列仍在）。
     */
    public static final String EMAIL_VERIFICATION_QUEUE = "official.email.verification";

    /** 面试预约异步落库 */
    public static final String INTERVIEW_BOOKING_QUEUE = "official.interview.booking.persist";

    /** 预约成功通知（邮件等，旧队列，保留兼容） */
    public static final String INTERVIEW_BOOKING_NOTIFICATION_QUEUE = "official.interview.booking.notification";

    /** 面试通知（预约成功、提醒、录取/未录取） */
    public static final String INTERVIEW_NOTIFICATION_QUEUE = "official.interview.notification";

    /** 飞书多维表格异步同步 */
    public static final String FEISHU_SYNC_QUEUE = "official.feishu.sync";

    @Bean
    public Queue emailVerificationQueue() {
        return QueueBuilder.durable(EMAIL_VERIFICATION_QUEUE).build();
    }

    @Bean
    public Queue interviewBookingQueue() {
        return QueueBuilder.durable(INTERVIEW_BOOKING_QUEUE).build();
    }

    @Bean
    public Queue interviewBookingNotificationQueue() {
        return QueueBuilder.durable(INTERVIEW_BOOKING_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Queue interviewNotificationQueue() {
        return QueueBuilder.durable(INTERVIEW_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Queue feishuSyncQueue() {
        return QueueBuilder.durable(FEISHU_SYNC_QUEUE).build();
    }

    /**
     * 将消息对象序列化为 JSON，消费者才能反序列化为 {@link club.boyuan.official.messaging.EmailVerificationMessage}。
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
