package club.boyuan.official.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：业务队列 + 死信交换机（DLX）/ 死信队列（DLQ）。
 * <p>消费重试耗尽且 {@code default-requeue-rejected=false} 时，消息进入对应 DLQ。
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String DEAD_LETTER_EXCHANGE = "official.dlx";

    public static final String EMAIL_VERIFICATION_QUEUE = "official.email.verification";
    public static final String INTERVIEW_BOOKING_QUEUE = "official.interview.booking.persist";
    public static final String INTERVIEW_NOTIFICATION_QUEUE = "official.interview.notification";
    public static final String FEISHU_SYNC_QUEUE = "official.feishu.sync";

    public static String dlqRoutingKey(String mainQueue) {
        return mainQueue + ".dlq";
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue emailVerificationQueue() {
        return businessQueue(EMAIL_VERIFICATION_QUEUE);
    }

    @Bean
    public Queue emailVerificationDlq() {
        return dlq(EMAIL_VERIFICATION_QUEUE);
    }

    @Bean
    public Binding emailVerificationDlqBinding() {
        return dlqBinding(emailVerificationDlq(), EMAIL_VERIFICATION_QUEUE);
    }

    @Bean
    public Queue interviewBookingQueue() {
        return businessQueue(INTERVIEW_BOOKING_QUEUE);
    }

    @Bean
    public Queue interviewBookingDlq() {
        return dlq(INTERVIEW_BOOKING_QUEUE);
    }

    @Bean
    public Binding interviewBookingDlqBinding() {
        return dlqBinding(interviewBookingDlq(), INTERVIEW_BOOKING_QUEUE);
    }

    @Bean
    public Queue interviewNotificationQueue() {
        return businessQueue(INTERVIEW_NOTIFICATION_QUEUE);
    }

    @Bean
    public Queue interviewNotificationDlq() {
        return dlq(INTERVIEW_NOTIFICATION_QUEUE);
    }

    @Bean
    public Binding interviewNotificationDlqBinding() {
        return dlqBinding(interviewNotificationDlq(), INTERVIEW_NOTIFICATION_QUEUE);
    }

    @Bean
    public Queue feishuSyncQueue() {
        return businessQueue(FEISHU_SYNC_QUEUE);
    }

    @Bean
    public Queue feishuSyncDlq() {
        return dlq(FEISHU_SYNC_QUEUE);
    }

    @Bean
    public Binding feishuSyncDlqBinding() {
        return dlqBinding(feishuSyncDlq(), FEISHU_SYNC_QUEUE);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 秒杀落库专用：批量拉取消息（最多 100 条或等待 200ms），让消费者把 N 次独立事务
     * 收敛为 1 次批量 UPDATE+INSERT，降低高并发下 interview_slot 行锁的排队等待。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory bookingBatchContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(100);
        return factory;
    }

    private static Queue businessQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(dlqRoutingKey(queueName))
                .build();
    }

    private static Queue dlq(String mainQueue) {
        return QueueBuilder.durable(dlqRoutingKey(mainQueue)).build();
    }

    private Binding dlqBinding(Queue dlq, String mainQueue) {
        return BindingBuilder.bind(dlq)
                .to(deadLetterExchange())
                .with(dlqRoutingKey(mainQueue));
    }
}
