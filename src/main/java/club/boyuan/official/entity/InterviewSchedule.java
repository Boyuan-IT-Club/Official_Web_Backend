package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 面试安排表
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_schedule")
public class InterviewSchedule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 面试安排ID
     */
    @TableId(value = "schedule_id", type = IdType.AUTO)
    private Integer scheduleId;

    /**
     * 简历ID
     */
    @TableField("resume_id")
    private Integer resumeId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 招募活动ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 分配ID
     */
    @TableField("slot_id")
    private Integer slotId;

    /**
     * 分配的面试具体时间
     */
    @TableField("interview_time")
    private LocalDateTime interviewTime;

    /**
     * 状态：0（未安排），1(已安排), 2(已取消)
     */
    @TableField("status")
    private Integer status;

    /**
     * 安排备注
     */
    @TableField("notes")
    private String notes;

    /**
     * 同步飞书状态：0(未同步), 1(已同步)
     */
    @TableField("sync_status")
    private Integer syncStatus;

    /**
     * 通知状态：0(未通知), 1(已通知)
     */
    @TableField("notif_status")
    private Integer notifStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;


}
