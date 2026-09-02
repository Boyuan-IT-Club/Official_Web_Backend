package club.boyuan.official.messaging;

import club.boyuan.official.infra.notification.mail.RecruitmentMails;
import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.common.utils.MessageUtils;
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
    /** 与 Redis 里验证码的 TTL 保持一致 */
    private static final int CODE_VALID_MINUTES = 5;

    private final MessageUtils messageUtils;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_VERIFICATION_QUEUE)
    public void handle(EmailVerificationMessage message) {
        if (message == null || message.getEmail() == null || message.getCode() == null) {
            log.warn("收到无效的邮箱验证码消息，已忽略: {}", message);
            return;
        }
        // 改发 HTML（与三封招新通知同一套视觉）。同时带纯文本兜底，
        // 关闭了 HTML 的客户端仍能看到验证码。
        var mail = RecruitmentMails.verificationCode(message.getCode(), CODE_VALID_MINUTES);
        messageUtils.sendHtmlEmail(message.getEmail(), EMAIL_SUBJECT, mail.html(), mail.plainText());
        log.info("邮箱验证码已通过 MQ 消费者发送, email={}", message.getEmail());
    }
}
