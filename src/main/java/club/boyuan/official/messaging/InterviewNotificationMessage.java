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
    /** 管理员补充说明，附加在模板正文之后（仅结果类通知） */
    private String customBody;
    /**
     * 简历 id。简历初筛未通过的通知发生在面试之前，
     * 既没有 scheduleId 也没有 resultId，收件人只能从简历定位。
     */
    private Integer resumeId;

    /**
     * 不带 resumeId 的老构造：结果类通知与场次类通知都用不到简历 id，
     * 保留它让既有调用点（含测试）不必为一个恒为 null 的参数改签名。
     */
    public InterviewNotificationMessage(InterviewNotificationType type, Integer scheduleId,
                                        Integer resultId, String requestId, String customBody) {
        this(type, scheduleId, resultId, requestId, customBody, null);
    }
}
