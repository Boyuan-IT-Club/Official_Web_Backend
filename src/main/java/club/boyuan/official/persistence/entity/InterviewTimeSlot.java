package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <p>
 * 面试时间窗（学生可勾选的大时段，如"周六上午"）
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_time_slot")
public class InterviewTimeSlot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "time_slot_id", type = IdType.AUTO)
    private Integer timeSlotId;

    /**
     * 招募周期ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 时段名称，如 周六上午
     */
    @TableField("slot_name")
    private String slotName;

    /**
     * 面试日期
     */
    @TableField("interview_date")
    private LocalDate interviewDate;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalTime endTime;

    /**
     * 状态：1(可选), 2(关闭)
     */
    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
