package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产者：把「发邮件」任务投递到队列，接口线程立即返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String email, String code) {
        EmailVerificationMessage message = new EmailVerificationMessage(email, code);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_VERIFICATION_QUEUE, message);
        log.info("邮箱验证码消息已投递到队列 {}, email={}", RabbitMQConfig.EMAIL_VERIFICATION_QUEUE, email);
    }
}
