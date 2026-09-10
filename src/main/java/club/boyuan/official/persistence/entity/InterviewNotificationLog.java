package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试通知发送记录，用于幂等与审计。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_notification_log")
public class InterviewNotificationLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("notification_type")
    private String notificationType;

    @TableField("schedule_id")
    private Integer scheduleId;

    @TableField("result_id")
    private Integer resultId;

    /**
     * 简历 ID（V44）。面试之前发出的通知（简历初筛未通过）没有 schedule/result 可挂，
     * 靠这一列才查得到「这个人通知过没有」。
     */
    @TableField("resume_id")
    private Integer resumeId;

    /**
     * 这一次发送的唯一标识（V37）。去重靠它而不是靠 (type, result_id)：
     * 前者只拦 MQ 重投同一条消息，后者会把管理员有意的重发也拦掉。
     * 历史行为 NULL。
     */
    @TableField("request_id")
    private String requestId;

    @TableField("recipient_email")
    private String recipientEmail;

    @TableField("sent_at")
    private LocalDateTime sentAt;
}
