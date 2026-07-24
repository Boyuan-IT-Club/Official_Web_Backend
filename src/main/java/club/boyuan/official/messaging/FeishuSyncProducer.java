package club.boyuan.official.messaging;

import club.boyuan.official.infra.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 飞书同步 MQ 生产者：只往队列 {@link club.boyuan.official.infra.config.RabbitMQConfig#FEISHU_SYNC_QUEUE}
 * 投递 taskId；具体导入参数在 Redis 任务里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 消息体仅含 taskId，减轻 MQ 体积并与 Redis 任务状态单一数据源对齐。 */
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FEISHU_SYNC_QUEUE, new FeishuSyncMessage(taskId));
        log.info("飞书同步任务已投递 taskId={}", taskId);
    }
}
