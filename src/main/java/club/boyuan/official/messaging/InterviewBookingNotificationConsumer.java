package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import club.boyuan.official.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费者：预约成功后异步发送邮件通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewBookingNotificationConsumer {

    private static final String SUBJECT = "面试预约成功通知";

    private final MessageUtils messageUtils;

    @RabbitListener(queues = RabbitMQConfig.INTERVIEW_BOOKING_NOTIFICATION_QUEUE)
    public void handle(InterviewBookingNotificationMessage message) {
        if (message == null) {
            return;
        }
        if (!StringUtils.hasText(message.getEmail())) {
            log.info("用户无邮箱，跳过预约通知 requestId={}", message.getRequestId());
            return;
        }
        String content = String.format(
                "您好%s，您已成功预约面试时段（预约编号 scheduleId=%d，时段 slotId=%d）。请登录官网查看详情。",
                StringUtils.hasText(message.getUserName()) ? "，" + message.getUserName() : "",
                message.getScheduleId(),
                message.getSlotId());
        try {
            messageUtils.sendEmail(message.getEmail(), SUBJECT, content);
            log.info("预约成功邮件已发送 requestId={}, email={}", message.getRequestId(), message.getEmail());
        } catch (Exception e) {
            log.error("预约通知邮件发送失败 requestId={}", message.getRequestId(), e);
            throw e;
        }
    }
}
