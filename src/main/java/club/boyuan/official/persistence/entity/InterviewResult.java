package club.boyuan.official.persistence.entity;

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
 * 面试结果表
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_result")
public class InterviewResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 结果ID
     */
    @TableId(value = "result_id", type = IdType.AUTO)
    private Integer resultId;

    /**
     * 面试安排ID
     */
    @TableField("schedule_id")
    private Integer scheduleId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Integer userId;

    /** 简历ID。结果脱离面试安排时（不能线下参加/未被分配）靠它定位候选人（V34） */
    @TableField("resume_id")
    private Integer resumeId;

    /** 招募周期ID。此前只能经 schedule 反查，无安排时查不到（V34） */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 最终决定：0(待定), 1(通过), 2(不通过), 3(待调剂)
     */
    @TableField("decision")
    private Integer decision;

    /**
     * 实际分配部门ID
     */
    @TableField("assigned_dept_id")
    private Integer assignedDeptId;

    /**
     * 决定人ID
     */
    @TableField("decision_by")
    private Integer decisionBy;

    /**
     * 决定时间
     */
    @TableField("decision_at")
    private LocalDateTime decisionAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 最近一次结果通知的发送时间；NULL = 从未通知。重发时更新（V27） */
    @TableField("notified_at")
    private LocalDateTime notifiedAt;


}
