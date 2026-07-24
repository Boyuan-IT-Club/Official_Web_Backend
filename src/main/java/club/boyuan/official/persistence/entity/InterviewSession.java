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
 * 面试场次（部门 × 时间窗 × 地点 × 容量）
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_session")
public class InterviewSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "session_id", type = IdType.AUTO)
    private Integer sessionId;

    /**
     * 招募周期ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 时间窗ID
     */
    @TableField("time_slot_id")
    private Integer timeSlotId;

    /**
     * 部门ID
     */
    @TableField("dept_id")
    private Integer deptId;

    /**
     * 面试地点
     */
    @TableField("location")
    private String location;

    /**
     * 容量（该场次可面人数）
     */
    @TableField("capacity")
    private Integer capacity;

    /**
     * 已分配人数
     */
    @TableField("current_occupied")
    private Integer currentOccupied;

    /**
     * 单人面试时长（分钟）
     */
    @TableField("interview_duration_minutes")
    private Integer interviewDurationMinutes;

    /**
     * 状态：1(可用), 2(关闭)
     */
    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
