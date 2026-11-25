package club.boyuan.official.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageOutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(OutboxEventType event, String aggregate, String key, Object payload) {
        try {
            jdbc.update("CALL delivery_queue_task(?,?,?,?)", aggregate, key, event.name(), json.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法保存事务发件箱消息", e);
        }
    }
}
