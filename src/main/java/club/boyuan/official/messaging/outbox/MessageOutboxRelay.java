package club.boyuan.official.messaging.outbox;

import club.boyuan.official.infra.config.OutboxProperties;
import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.messaging.DeliveryLedger;
import club.boyuan.official.messaging.FeishuSyncMessage;
import club.boyuan.official.messaging.InterviewBookingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageOutboxRelay {
    private final OutboxProperties properties;
    private final DeliveryLedger ledger;
    private final ObjectMapper json;
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:1000}")
    public void relayPending() {
        if (!properties.isEnabled()) return;
        for (int i = 0; i < properties.getBatchSize(); i++) {
            String token = UUID.randomUUID().toString();
            var row = ledger.lease(token, properties.getMaxRetries());
            if (row == null) return;
            var correlation = new CorrelationData(token);
            try {
                String queue;
                Object body;
                switch (row.event()) {
                    case "EMAIL_NOTIFICATION" -> { queue = RabbitMQConfig.INTERVIEW_NOTIFICATION_QUEUE; body = row.mail(); }
                    case "FEISHU_SYNC" -> { queue = RabbitMQConfig.FEISHU_SYNC_QUEUE; body = json.readValue(row.payload(), FeishuSyncMessage.class); }
                    case "INTERVIEW_BOOKING_PERSIST" -> { queue = RabbitMQConfig.INTERVIEW_BOOKING_QUEUE; body = json.readValue(row.payload(), InterviewBookingMessage.class); }
                    default -> throw new IllegalArgumentException("Unknown outbox event");
                }
                rabbit.convertAndSend("", queue, body, message -> {
                    message.getMessageProperties().setMessageId("delivery-" + row.id());
                    return message;
                }, correlation);
                boolean accepted = correlation.getFuture().get(8, TimeUnit.SECONDS).isAck() && correlation.getReturned() == null;
                ledger.publication(row.id(), token, accepted, accepted ? null : "Broker did not confirm routing", properties.getMaxRetries());
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                ledger.publication(row.id(), token, false, e.getClass().getSimpleName(), properties.getMaxRetries());
                log.warn("消息队列待复核或重试 deliveryId={}", row.id(), e);
                return;
            }
        }
    }
}
