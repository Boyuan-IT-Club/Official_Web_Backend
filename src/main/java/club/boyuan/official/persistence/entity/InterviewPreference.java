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
 * 学生面试志愿部门（第一 / 第二志愿），一份简历一条
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "interview_preference", autoResultMap = true)
public class InterviewPreference implements Serializable {

    @TableField(value = "accepted_time_slot_ids", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<Integer> acceptedTimeSlotIds;

    private static final long serialVersionUID = 1L;

    @TableId(value = "preference_id", type = IdType.AUTO)
    private Integer preferenceId;

    /**
     * 简历ID
     */
    @TableField("resume_id")
    private Integer resumeId;

    /**
     * 招募周期ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 第一志愿部门
     */
    @TableField("first_dept_id")
    private Integer firstDeptId;

    /**
     * 第二志愿部门
     */
    @TableField(value = "second_dept_id", updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Integer secondDeptId;

    /**
     * 提交时间
     */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public java.util.List<Integer> acceptedTimes() {
        return acceptedTimeSlotIds == null ? java.util.List.of() : acceptedTimeSlotIds;
    }
}
