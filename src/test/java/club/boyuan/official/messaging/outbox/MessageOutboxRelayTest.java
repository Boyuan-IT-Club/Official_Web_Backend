package club.boyuan.official.messaging.outbox;

import club.boyuan.official.infra.config.OutboxProperties;
import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.messaging.DeliveryLedger;
import club.boyuan.official.messaging.FeishuSyncMessage;
import club.boyuan.official.messaging.InterviewBookingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageOutboxRelayTest {
    final OutboxProperties properties = new OutboxProperties();
    final DeliveryLedger ledger = mock(DeliveryLedger.class);
    final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    final MessageOutboxRelay relay = new MessageOutboxRelay(properties, ledger, new ObjectMapper(), rabbit);

    @Test void standbyDoesNotClaim() {
        properties.setEnabled(false);
        relay.relayPending();
        verifyNoInteractions(ledger, rabbit);
    }

    @Test void feishuTaskWaitsForBrokerAcceptance() {
        exercise("FEISHU_SYNC","{\"taskId\":99}",RabbitMQConfig.FEISHU_SYNC_QUEUE,FeishuSyncMessage.class,true);
    }

    @Test void bookingKeepsItsOwnProtocol() {
        exercise("INTERVIEW_BOOKING_PERSIST","{\"requestId\":\"booking-1\"}",RabbitMQConfig.INTERVIEW_BOOKING_QUEUE,InterviewBookingMessage.class,true);
    }

    @Test void nackDoesNotConfirmPublication() {
        exercise("FEISHU_SYNC","{\"taskId\":99}",RabbitMQConfig.FEISHU_SYNC_QUEUE,FeishuSyncMessage.class,false);
    }

    private void exercise(String event, String payload, String queue, Class<?> kind, boolean ack) {
        when(ledger.lease(anyString(),anyInt())).thenReturn(new DeliveryLedger.Dispatch(7,event,payload,null),null);
        doAnswer(inv -> {
            CorrelationData c=inv.getArgument(4);
            c.getFuture().complete(new CorrelationData.Confirm(ack,ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq(""),eq(queue),isA(kind),any(MessagePostProcessor.class),any(CorrelationData.class));
        relay.relayPending();
        verify(ledger).publication(eq(7L),anyString(),eq(ack),nullable(String.class),eq(properties.getMaxRetries()));
    }
}
