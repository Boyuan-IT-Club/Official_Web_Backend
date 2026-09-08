package club.boyuan.official.messaging;

import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publish(InterviewNotificationMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.INTERVIEW_NOTIFICATION_QUEUE, message);
        log.info("面试通知消息已投递 type={}, scheduleId={}, resultId={}",
                message.getType(), message.getScheduleId(), message.getResultId());
    }

    public void publishBookingSuccess(Integer scheduleId, String requestId) {
        publish(new InterviewNotificationMessage(
                InterviewNotificationType.BOOKING_SUCCESS, scheduleId, null, requestId, null));
    }

    public void publishReminder(InterviewNotificationType type, Integer scheduleId) {
        publish(new InterviewNotificationMessage(type, scheduleId, null, null, null));
    }

    public void publishResult(Integer resultId, String customBody) {
        // 每次入队一个新的 requestId：消费端按它去重。
        // 这样 MQ 重投同一条消息会被拦（同 id），管理员点第二次重发会放行（新 id）。
        // 原来这里传 null，消费端只能退回按 (type, resultId) 去重，把重发也拦掉了。
        publish(new InterviewNotificationMessage(
                null, null, resultId, java.util.UUID.randomUUID().toString(), customBody));
    }
}
