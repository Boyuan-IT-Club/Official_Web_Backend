package club.boyuan.official.messaging;

import club.boyuan.official.outbox.MessageOutboxService;
import club.boyuan.official.outbox.OutboxAggregateType;
import club.boyuan.official.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 统一 MQ 投递：Outbox 开启时写入发件箱，否则直发 RabbitMQ。
 */
@Component
@RequiredArgsConstructor
public class ReliableMessagePublisher {

    private final MessageOutboxService messageOutboxService;
    private final InterviewBookingProducer bookingProducer;
    private final FeishuSyncProducer feishuSyncProducer;
    private final InterviewNotificationProducer notificationProducer;
    private final EmailVerificationProducer emailVerificationProducer;

    public void publishBookingPersist(InterviewBookingMessage message) {
        if (messageOutboxService.isEnabled()) {
            messageOutboxService.enqueue(
                    OutboxEventType.INTERVIEW_BOOKING_PERSIST,
                    OutboxAggregateType.INTERVIEW_BOOKING,
                    message.getRequestId(),
                    message);
        } else {
            bookingProducer.publishPersist(message);
        }
    }

    public void publishFeishuSync(String taskId) {
        Long id = Long.parseLong(taskId);
        if (messageOutboxService.isEnabled()) {
            messageOutboxService.enqueue(
                    OutboxEventType.FEISHU_SYNC,
                    OutboxAggregateType.FEISHU_SYNC,
                    taskId,
                    new FeishuSyncMessage(id));
        } else {
            feishuSyncProducer.publish(id);
        }
    }

    public void publishInterviewNotification(InterviewNotificationMessage message) {
        String aggregateId = resolveNotificationAggregateId(message);
        if (messageOutboxService.isEnabled()) {
            messageOutboxService.enqueue(
                    OutboxEventType.INTERVIEW_NOTIFICATION,
                    OutboxAggregateType.INTERVIEW_NOTIFICATION,
                    aggregateId,
                    message);
        } else {
            notificationProducer.publish(message);
        }
    }

    public void publishEmailVerification(String email, String code) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        EmailVerificationMessage message = new EmailVerificationMessage(messageId, email, code);
        if (messageOutboxService.isEnabled()) {
            messageOutboxService.enqueue(
                    OutboxEventType.EMAIL_VERIFICATION,
                    OutboxAggregateType.EMAIL_VERIFICATION,
                    messageId,
                    message);
        } else {
            emailVerificationProducer.publish(message);
        }
    }

    private static String resolveNotificationAggregateId(InterviewNotificationMessage message) {
        if (message.getScheduleId() != null) {
            return "schedule:" + message.getScheduleId() + ":" + message.getType();
        }
        if (message.getResultId() != null) {
            return "result:" + message.getResultId() + ":" + message.getType();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
