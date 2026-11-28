package club.boyuan.official.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.messaging.InterviewNotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitMessageCompatibilityTest {
    @Test
    void rollingVersionsCanReadEachOthersInterviewNotifications() throws Exception {
        var converter = new RabbitMQConfig().jsonMessageConverter();
        var headers = new MessageProperties();
        headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        headers.setHeader("__TypeId__", InterviewNotificationMessage.class.getName());
        // Captured from the notification converter on the 3.5.16 migration build.
        String json = """
                {"type":"EVE_REMINDER","scheduleId":7,"resultId":null,"requestId":null,"customBody":null,
                 "resumeId":null,"deliveryId":9007199254740993,"scheduleRevision":3}
                """;
        var parsed = (InterviewNotificationMessage) converter.fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), headers));
        assertThat(parsed.getType()).isEqualTo(InterviewNotificationType.EVE_REMINDER);
        assertThat(parsed.getScheduleId()).isEqualTo(7);
        assertThat(parsed.getScheduleRevision()).isEqualTo(3);
        assertThat(parsed.getDeliveryId()).isEqualTo(9007199254740993L);

        Message outgoing = converter.toMessage(parsed, new MessageProperties());
        assertThat(outgoing.getMessageProperties().getHeaders()).containsEntry(
                "__TypeId__", InterviewNotificationMessage.class.getName());
        assertThat(new ObjectMapper().readValue(outgoing.getBody(), InterviewNotificationMessage.class))
                .isEqualTo(parsed);
    }
}
