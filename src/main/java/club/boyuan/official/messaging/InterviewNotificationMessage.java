package club.boyuan.official.messaging;

import club.boyuan.official.infra.notification.InterviewNotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 面试通知异步消息（预约成功、提醒、录取/未录取）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewNotificationMessage implements Serializable {

    private InterviewNotificationType type;
    private Integer scheduleId;
    private Integer resultId;
    /** 秒杀预约 requestId，便于日志追踪 */
    private String requestId;
    /** 管理员自定义正文，非空时覆盖模板（仅结果通知） */
    private String customBody;
}
