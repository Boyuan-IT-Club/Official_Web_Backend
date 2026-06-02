package club.boyuan.official.outbox;

import club.boyuan.official.config.OutboxProperties;
import club.boyuan.official.entity.MessageOutbox;
import club.boyuan.official.mapper.MessageOutboxMapper;
import club.boyuan.official.messaging.FeishuSyncMessage;
import club.boyuan.official.messaging.FeishuSyncProducer;
import club.boyuan.official.messaging.InterviewBookingMessage;
import club.boyuan.official.messaging.InterviewBookingProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 扫描 PENDING 发件箱记录并投递 RabbitMQ。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageOutboxRelay {

    private final OutboxProperties outboxProperties;
    private final MessageOutboxMapper messageOutboxMapper;
    private final ObjectMapper objectMapper;
    private final InterviewBookingProducer bookingProducer;
    private final FeishuSyncProducer feishuSyncProducer;

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:1000}")
    @Transactional(rollbackFor = Exception.class)
    public void relayPending() {
        if (!outboxProperties.isEnabled()) {
            return;
        }
        List<MessageOutbox> rows = messageOutboxMapper.selectPendingForRelay(
                outboxProperties.getBatchSize(),
                outboxProperties.getMaxRetries());
        for (MessageOutbox row : rows) {
            dispatchOne(row);
        }
    }

    private void dispatchOne(MessageOutbox row) {
        try {
            OutboxEventType type = OutboxEventType.valueOf(row.getEventType());
            switch (type) {
                case INTERVIEW_BOOKING_PERSIST -> {
                    InterviewBookingMessage message = objectMapper.readValue(
                            row.getPayload(), InterviewBookingMessage.class);
                    bookingProducer.publishPersist(message);
                }
                case FEISHU_SYNC -> {
                    FeishuSyncMessage message = objectMapper.readValue(
                            row.getPayload(), FeishuSyncMessage.class);
                    feishuSyncProducer.publish(message.getTaskId());
                }
                default -> throw new IllegalStateException("未知 Outbox 事件: " + type);
            }
            int updated = messageOutboxMapper.markSent(row.getId());
            if (updated == 0) {
                log.warn("Outbox 标记 SENT 失败，可能已被其他实例处理 id={}", row.getId());
            }
        } catch (Exception ex) {
            String err = truncate(ex.getMessage());
            messageOutboxMapper.incrementRetry(row.getId(), err);
            if (row.getRetryCount() + 1 >= outboxProperties.getMaxRetries()) {
                messageOutboxMapper.markFailed(row.getId(), err);
                log.error("Outbox 投递永久失败 id={}, event={}", row.getId(), row.getEventType(), ex);
            } else {
                log.warn("Outbox 投递失败将重试 id={}, event={}, retry={}", row.getId(), row.getEventType(),
                        row.getRetryCount() + 1, ex);
            }
        }
    }

    private static String truncate(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
