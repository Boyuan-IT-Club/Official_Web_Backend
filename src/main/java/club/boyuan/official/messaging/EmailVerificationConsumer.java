package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import club.boyuan.official.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费者：从队列取出消息后调用 SMTP 真正发信；失败时由 Spring AMQP 按配置重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {

    private static final String EMAIL_SUBJECT = "邮箱验证码";

    private final MessageUtils messageUtils;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_VERIFICATION_QUEUE)
    public void handle(EmailVerificationMessage message) {
        if (message == null || message.getEmail() == null || message.getCode() == null) {
            log.warn("收到无效的邮箱验证码消息，已忽略: {}", message);
            return;
        }
        String content = "您的验证码是：" + message.getCode() + "，有效期5分钟";
        messageUtils.sendEmail(message.getEmail(), EMAIL_SUBJECT, content);
        log.info("邮箱验证码已通过 MQ 消费者发送, email={}", message.getEmail());
    }
}
