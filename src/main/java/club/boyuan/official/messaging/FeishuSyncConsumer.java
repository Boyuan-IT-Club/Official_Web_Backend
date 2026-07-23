package club.boyuan.official.messaging;

import club.boyuan.official.infra.config.RabbitMQConfig;
import club.boyuan.official.domain.interview.service.InterviewFeishuImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 飞书同步 MQ 消费者：从队列取 taskId，委托 {@link InterviewFeishuImportService#runImportTask} 执行。
 * <p>抛异常会触发 Spring AMQP 重试（见 application.yml listener.simple.retry）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuSyncConsumer {

    private final InterviewFeishuImportService feishuImportService;

    @RabbitListener(queues = RabbitMQConfig.FEISHU_SYNC_QUEUE)
    public void handle(FeishuSyncMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("无效飞书同步消息，已忽略");
            return;
        }
        try {
            feishuImportService.runImportTask(message.getTaskId());
        } catch (Exception ex) {
            log.error("飞书同步消费失败 taskId={}", message.getTaskId(), ex);
            throw ex;
        }
    }
}
