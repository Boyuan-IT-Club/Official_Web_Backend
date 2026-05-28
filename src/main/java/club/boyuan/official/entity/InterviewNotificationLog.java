package club.boyuan.official.entity;

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

    @TableField("recipient_email")
    private String recipientEmail;

    @TableField("sent_at")
    private LocalDateTime sentAt;
}
