package club.boyuan.official.outbox;

import club.boyuan.official.config.OutboxProperties;
import club.boyuan.official.entity.MessageOutbox;
import club.boyuan.official.mapper.MessageOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 事务发件箱：与业务在同一事务内写入，由 {@link MessageOutboxRelay} 异步投递 MQ。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageOutboxService {

    private final MessageOutboxMapper messageOutboxMapper;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(OutboxEventType eventType, String aggregateType, String aggregateId, Object payload) {
        if (!outboxProperties.isEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageOutbox row = new MessageOutbox()
                    .setAggregateType(aggregateType)
                    .setAggregateId(aggregateId)
                    .setEventType(eventType.name())
                    .setPayload(json)
                    .setStatus(OutboxStatus.PENDING)
                    .setRetryCount(0)
                    .setCreatedAt(LocalDateTime.now());
            messageOutboxMapper.insert(row);
            log.debug("Outbox 入队 event={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox 序列化失败", e);
        } catch (DuplicateKeyException e) {
            log.info("Outbox 已存在，跳过重复入队 event={}, aggregateId={}", eventType, aggregateId);
        }
    }

    public boolean isEnabled() {
        return outboxProperties.isEnabled();
    }
}
