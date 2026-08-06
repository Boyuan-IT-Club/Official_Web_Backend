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
 * <p>
 * 面试改期申请
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_reschedule_request")
public class InterviewRescheduleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已同意（由管理员在「分配与调剂」中重排） */
    public static final int STATUS_APPROVED = 1;
    /** 状态：已拒绝 */
    public static final int STATUS_REJECTED = 2;

    @TableId(value = "request_id", type = IdType.AUTO)
    private Integer requestId;

    @TableField("schedule_id")
    private Integer scheduleId;

    @TableField("resume_id")
    private Integer resumeId;

    @TableField("user_id")
    private Integer userId;

    @TableField("cycle_id")
    private Integer cycleId;

    @TableField("reason")
    private String reason;

    /** 期望时间窗ID列表（逗号分隔，可空） */
    @TableField("preferred_time_slot_ids")
    private String preferredTimeSlotIds;

    @TableField("status")
    private Integer status;

    @TableField("admin_note")
    private String adminNote;

    @TableField("handled_by")
    private Integer handledBy;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
