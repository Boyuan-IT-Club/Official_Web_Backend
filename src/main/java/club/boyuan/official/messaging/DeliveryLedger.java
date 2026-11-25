package club.boyuan.official.messaging;

import club.boyuan.official.infra.notification.InterviewNotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Each mail decision below is a short transaction committed by its definer procedure. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DeliveryLedger {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Decision(Long id, String state) {
        public boolean accepted() { return "PENDING".equals(state) || "SENDING".equals(state) || "SENT".equals(state); }
    }

    public record Dispatch(long id, String event, String payload, InterviewNotificationMessage mail) {}

    public Decision enqueue(InterviewNotificationMessage m) {
        return jdbc.queryForObject("CALL delivery_enqueue(?,?,?,?,?,?)",
                (r, n) -> new Decision(r.getObject("delivery_id", Long.class), r.getString("mail_state")),
                name(m), m.getScheduleId(), m.getResultId(), m.getResumeId(), m.getRequestId(), m.getCustomBody());
    }

    public Dispatch lease(String token, int attempts) {
        List<Dispatch> rows = jdbc.query("CALL delivery_lease_queue(?,?)",
                (r, n) -> new Dispatch(r.getLong("delivery_id"), r.getString("event_type"), r.getString("payload"),
                        "TASK".equals(r.getString("delivery_kind")) ? null : mail(r)), token, attempts);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void publication(long id, String token, boolean accepted, String error, int attempts) {
        jdbc.update("CALL delivery_ack_queue(?,?,?,?,?)", id, token, accepted, error, attempts);
    }

    public InterviewNotificationMessage observe(InterviewNotificationMessage envelope, String brokerId) {
        if (envelope.getDeliveryId() == null) {
            String source = brokerId != null ? "rabbit:" + brokerId : envelope.getRequestId() == null ? null
                    : "legacy:" + name(envelope) + ":" + envelope.getRequestId();
            String key = source == null ? "manifest:" + UUID.randomUUID() : "mq:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
            try {
                String body = json.writeValueAsString(java.util.Map.of("message", envelope, "messageId", brokerId == null ? "" : brokerId));
                jdbc.update("CALL delivery_quarantine(?,?,?,?,?,?)", key, body, name(envelope), envelope.getScheduleId(), envelope.getResultId(), envelope.getResumeId());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("无法归档旧通知", e);
            }
            return null;
        }
        List<InterviewNotificationMessage> messages = jdbc.query("CALL delivery_observe_mail(?,?,?,?)",
                (r, n) -> mail(r), envelope.getDeliveryId(), envelope.getRequestId(), name(envelope), envelope.getScheduleRevision());
        return messages.isEmpty() ? null : messages.get(0);
    }

    public boolean claim(InterviewNotificationMessage m, String token, String address, LocalDateTime interviewTime) {
        return Boolean.TRUE.equals(jdbc.queryForObject("CALL delivery_claim_mail(?,?,?,?,?,?,?)", Boolean.class,
                m.getDeliveryId(), m.getRequestId(), name(m), m.getScheduleRevision(), token, address, interviewTime));
    }

    public void finish(InterviewNotificationMessage m, String token, boolean accepted, String diagnostic) {
        jdbc.update("CALL delivery_finish_mail(?,?,?,?)", m.getDeliveryId(), token, accepted, diagnostic);
    }

    public void reject(InterviewNotificationMessage m, String state, String reason) {
        jdbc.update("CALL delivery_reject_preflight(?,?,?,?)", m.getDeliveryId(), m.getRequestId(), state, reason);
    }

    private InterviewNotificationMessage mail(ResultSet r) throws SQLException {
        String extra = null;
        String payload = r.getString("payload");
        if (payload != null) {
            try {
                extra = json.readTree(payload).path("customBody").asText(null);
            } catch (JsonProcessingException e) {
                throw new SQLException("无法解析已持久化的通知正文", e);
            }
        }
        var m = new InterviewNotificationMessage(InterviewNotificationType.valueOf(r.getString("notification_type")),
                r.getObject("schedule_id", Integer.class), r.getObject("result_id", Integer.class), r.getString("request_id"),
                extra, r.getObject("resume_id", Integer.class));
        m.setDeliveryId(r.getLong("delivery_id"));
        m.setScheduleRevision(r.getObject("schedule_revision", Integer.class));
        return m;
    }

    private static String name(InterviewNotificationMessage m) { return m.getType() == null ? null : m.getType().name(); }
}
