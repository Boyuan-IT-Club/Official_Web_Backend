package club.boyuan.official.messaging;

import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.domain.interview.service.impl.InterviewNotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费者：异步发送面试相关邮件（预约成功、提醒、录取/未录取）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNotificationConsumer {

    private final InterviewNotificationServiceImpl notificationService;

    @RabbitListener(queues = RabbitMQConfig.INTERVIEW_NOTIFICATION_QUEUE)
    public void handle(InterviewNotificationMessage message) {
        try {
            notificationService.deliver(message);
        } catch (Exception e) {
            log.error("面试通知发送失败 type={}, scheduleId={}, resultId={}",
                    message != null ? message.getType() : null,
                    message != null ? message.getScheduleId() : null,
                    message != null ? message.getResultId() : null,
                    e);
            throw e;
        }
    }
}
