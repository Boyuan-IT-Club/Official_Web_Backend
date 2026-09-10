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

    /*
     * 以下两个不是本表的列，是 selectResultPage 联表查出来的展示字段。
     *
     * exist = false 必须写：不写的话 MyBatis-Plus 会把它们当成真实列，
     * 拼进 INSERT/UPDATE 直接报 Unknown column。
     *
     * 之前实体里根本没有这两个字段——SQL 明明 SELECT 了 u.name as user_name，
     * 查出来无处安放就被丢掉，管理端只好去「面试安排名册」里凑名字。
     * 而没有面试安排的同学不在那份名册里（V34 之后他们也进结果名单了），
     * 于是姓名退化成「用户#14」，尽管库里存着「叶晓良」。
     */
    @TableField(value = "user_name", exist = false)
    private String userName;

    /** 生效面试安排的时间（联表回退取得）；无安排为 null */
    @TableField(value = "interview_time", exist = false)
    private java.time.LocalDateTime interviewTime;

    /** 简历平均分；未打过分为 null（列默认 0 不代表打过 0 分），联表展示字段 */
    @TableField(value = "resume_score", exist = false)
    private Integer resumeScore;

    /** 面试评价加权总分（interview_evaluation.total_score），无评价为 null */
    @TableField(value = "eval_total_score", exist = false)
    private java.math.BigDecimal evalTotalScore;

    /** 面试官共同结论：1 倾向通过 2 待定 3 不倾向，无评价为 null */
    @TableField(value = "eval_recommendation", exist = false)
    private Integer evalRecommendation;

    /** 第一/第二志愿部门名（interview_preference 联表），录取分配时参考 */
    @TableField(value = "first_dept_name", exist = false)
    private String firstDeptName;

    @TableField(value = "second_dept_name", exist = false)
    private String secondDeptName;

    @TableField(value = "department_name", exist = false)
    private String departmentName;
}
