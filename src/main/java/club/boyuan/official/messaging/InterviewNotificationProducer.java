package club.boyuan.official.messaging;

import club.boyuan.official.infra.notification.InterviewNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNotificationProducer {

    private final DeliveryLedger ledger;

    public boolean publish(InterviewNotificationMessage message) {
        if (message.getRequestId() == null) message.setRequestId(java.util.UUID.randomUUID().toString());
        return ledger.enqueue(message).accepted();
    }

    public boolean publishBookingSuccess(Integer scheduleId, String requestId) {
        return publish(new InterviewNotificationMessage(
                InterviewNotificationType.BOOKING_SUCCESS, scheduleId, null, requestId, null, null));
    }

    public boolean publishReminder(InterviewNotificationType type, Integer scheduleId) {
        return publish(new InterviewNotificationMessage(type, scheduleId, null, null, null, null));
    }

    public boolean publishResult(Integer resultId, String customBody) {
        return publishResult(resultId, customBody, java.util.UUID.randomUUID().toString());
    }

    public boolean publishResult(Integer resultId, String customBody, String requestId) {
        return publish(new InterviewNotificationMessage(
                null, null, resultId, requestId, customBody, null));
    }

    /** 简历未通过初筛的通知。与结果通知一样每次一个新 requestId，允许重发。 */
    public boolean publishResumeRejected(Integer resumeId, String customBody) {
        return publishResumeRejected(resumeId, customBody, java.util.UUID.randomUUID().toString());
    }

    public boolean publishResumeRejected(Integer resumeId, String customBody, String requestId) {
        return publish(new InterviewNotificationMessage(
                InterviewNotificationType.RESUME_REJECTED, null, null,
                requestId, customBody, resumeId));
    }
}
