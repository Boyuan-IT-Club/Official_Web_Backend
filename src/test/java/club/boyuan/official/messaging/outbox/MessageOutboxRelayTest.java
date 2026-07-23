package club.boyuan.official.messaging.outbox;

import club.boyuan.official.infra.config.OutboxProperties;
import club.boyuan.official.persistence.entity.MessageOutbox;
import club.boyuan.official.persistence.mapper.MessageOutboxMapper;
import club.boyuan.official.messaging.FeishuSyncMessage;
import club.boyuan.official.messaging.FeishuSyncProducer;
import club.boyuan.official.messaging.InterviewBookingMessage;
import club.boyuan.official.messaging.InterviewBookingProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageOutboxRelayTest {

    @Mock private OutboxProperties outboxProperties;
    @Mock private MessageOutboxMapper messageOutboxMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private InterviewBookingProducer bookingProducer;
    @Mock private FeishuSyncProducer feishuSyncProducer;

    @InjectMocks private MessageOutboxRelay relay;

    private MessageOutbox row(long id, OutboxEventType type, int retryCount) {
        return new MessageOutbox()
                .setId(id)
                .setEventType(type.name())
                .setPayload("{}")
                .setRetryCount(retryCount);
    }

    @Test
    void disabled_doesNothing() {
        when(outboxProperties.isEnabled()).thenReturn(false);

        relay.relayPending();

        verify(messageOutboxMapper, never()).selectPendingForRelay(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void bookingEvent_publishesAndMarksSent() throws Exception {
        enable();
        MessageOutbox r = row(1L, OutboxEventType.INTERVIEW_BOOKING_PERSIST, 0);
        when(messageOutboxMapper.selectPendingForRelay(10, 5)).thenReturn(List.of(r));
        InterviewBookingMessage msg = new InterviewBookingMessage();
        when(objectMapper.readValue(anyString(), eq(InterviewBookingMessage.class))).thenReturn(msg);
        when(messageOutboxMapper.markSent(1L)).thenReturn(1);

        relay.relayPending();

        verify(bookingProducer).publishPersist(msg);
        verify(messageOutboxMapper).markSent(1L);
        verify(messageOutboxMapper, never()).incrementRetry(eq(1L), anyString());
        verify(messageOutboxMapper, never()).markFailed(eq(1L), anyString());
    }

    @Test
    void feishuEvent_publishesTaskId() throws Exception {
        enable();
        MessageOutbox r = row(2L, OutboxEventType.FEISHU_SYNC, 0);
        when(messageOutboxMapper.selectPendingForRelay(10, 5)).thenReturn(List.of(r));
        FeishuSyncMessage msg = new FeishuSyncMessage();
        msg.setTaskId(99L);
        when(objectMapper.readValue(anyString(), eq(FeishuSyncMessage.class))).thenReturn(msg);

        relay.relayPending();

        verify(feishuSyncProducer).publish(99L);
        verify(messageOutboxMapper).markSent(2L);
    }

    @Test
    void dispatchFailure_withRetriesLeft_incrementsRetryOnly() throws Exception {
        enable();
        MessageOutbox r = row(3L, OutboxEventType.INTERVIEW_BOOKING_PERSIST, 0);
        when(messageOutboxMapper.selectPendingForRelay(10, 5)).thenReturn(List.of(r));
        when(objectMapper.readValue(anyString(), eq(InterviewBookingMessage.class)))
                .thenReturn(new InterviewBookingMessage());
        org.mockito.Mockito.doThrow(new RuntimeException("mq down"))
                .when(bookingProducer).publishPersist(org.mockito.ArgumentMatchers.any());

        relay.relayPending();

        verify(messageOutboxMapper).incrementRetry(eq(3L), anyString());
        verify(messageOutboxMapper, never()).markFailed(eq(3L), anyString());
    }

    @Test
    void dispatchFailure_whenRetriesExhausted_marksFailed() throws Exception {
        enable();
        // retryCount=4, maxRetries=5 -> 4+1 >= 5 触发永久失败
        MessageOutbox r = row(4L, OutboxEventType.INTERVIEW_BOOKING_PERSIST, 4);
        when(messageOutboxMapper.selectPendingForRelay(10, 5)).thenReturn(List.of(r));
        when(objectMapper.readValue(anyString(), eq(InterviewBookingMessage.class)))
                .thenReturn(new InterviewBookingMessage());
        org.mockito.Mockito.doThrow(new RuntimeException("mq down"))
                .when(bookingProducer).publishPersist(org.mockito.ArgumentMatchers.any());

        relay.relayPending();

        verify(messageOutboxMapper).incrementRetry(eq(4L), anyString());
        verify(messageOutboxMapper).markFailed(eq(4L), anyString());
    }

    private void enable() {
        when(outboxProperties.isEnabled()).thenReturn(true);
        when(outboxProperties.getBatchSize()).thenReturn(10);
        when(outboxProperties.getMaxRetries()).thenReturn(5);
    }
}
