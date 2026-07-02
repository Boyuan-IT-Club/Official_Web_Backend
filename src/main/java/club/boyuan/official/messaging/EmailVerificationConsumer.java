package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import club.boyuan.official.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 消费者：从队列取出消息后调用 SMTP 真正发信；失败时由 Spring AMQP 按配置重试。
 * <p>幂等：{@code email:verify:processed:{messageId}} Redis SETNX。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {

    private static final String EMAIL_SUBJECT = "邮箱验证码";
    private static final String PROCESSED_PREFIX = "email:verify:processed:";

    private final MessageUtils messageUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_VERIFICATION_QUEUE)
    public void handle(EmailVerificationMessage message) {
        if (message == null || !StringUtils.hasText(message.getEmail()) || !StringUtils.hasText(message.getCode())) {
            log.warn("收到无效的邮箱验证码消息，已忽略: {}", message);
            return;
        }
        if (!StringUtils.hasText(message.getMessageId())) {
            log.warn("邮箱验证码消息缺少 messageId，已忽略 email={}", message.getEmail());
            return;
        }

        String processedKey = PROCESSED_PREFIX + message.getMessageId();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(processedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(first)) {
            log.info("邮箱验证码消息已处理过，跳过 messageId={}", message.getMessageId());
            return;
        }

        try {
            String content = "您的验证码是：" + message.getCode() + "，有效期5分钟";
            messageUtils.sendEmail(message.getEmail(), EMAIL_SUBJECT, content);
            log.info("邮箱验证码已通过 MQ 消费者发送, messageId={}, email={}",
                    message.getMessageId(), message.getEmail());
        } catch (Exception e) {
            stringRedisTemplate.delete(processedKey);
            throw e;
        }
    }
}
