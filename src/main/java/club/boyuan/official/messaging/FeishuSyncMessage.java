package club.boyuan.official.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RabbitMQ 消息体：只携带 Redis 里的任务 ID。
 * 导入条件（cycleId、forceUpdate 等）以 {@link club.boyuan.official.integration.feishu.FeishuSyncTaskRecord} 为准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeishuSyncMessage implements Serializable {

    private Long taskId;
}
