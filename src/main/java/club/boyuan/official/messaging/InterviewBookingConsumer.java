package club.boyuan.official.messaging;

import club.boyuan.official.infra.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Retired bookings are acknowledged only after their original envelope is durable. */
@Component
@RequiredArgsConstructor
public class InterviewBookingConsumer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @RabbitListener(queues = RabbitMQConfig.INTERVIEW_BOOKING_QUEUE)
    public void handlePersist(InterviewBookingMessage booking, org.springframework.amqp.core.Message brokerMessage)
            throws JsonProcessingException {
        String key = booking.getRequestId();
        if (key == null || key.isBlank()) {
            String brokerId = brokerMessage.getMessageProperties().getMessageId();
            key = "unidentified:" + (brokerId == null ? UUID.randomUUID() : UUID.nameUUIDFromBytes(
                    brokerId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        var evidence = json.createObjectNode();
        evidence.set("booking", json.valueToTree(booking));
        evidence.put("messageId", brokerMessage.getMessageProperties().getMessageId());
        evidence.put("wireBody", java.util.Base64.getEncoder().encodeToString(brokerMessage.getBody()));
        jdbc.update("CALL delivery_archive_booking(?,?)", key, json.writeValueAsString(evidence));
    }
}
