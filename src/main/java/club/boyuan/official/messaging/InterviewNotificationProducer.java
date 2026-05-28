package club.boyuan.official.messaging;

import club.boyuan.official.config.RabbitMQConfig;
import club.boyuan.official.notification.InterviewNotificationType;
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
        publish(new InterviewNotificationMessage(null, null, resultId, null, customBody));
    }
}
