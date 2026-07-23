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
 * 学生可接受的面试时间窗（多选）
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_preference_time")
public class InterviewPreferenceTime implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 简历ID
     */
    @TableField("resume_id")
    private Integer resumeId;

    /**
     * 时间窗ID
     */
    @TableField("time_slot_id")
    private Integer timeSlotId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
