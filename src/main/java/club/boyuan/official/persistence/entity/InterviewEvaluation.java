package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 面试评价：一位面试官对一位候选人一行。
 * 由协同服务从 Y.Doc 物化写入；编辑期真源是 CRDT 文档，落库后业务真源是本表，
 * 下游（评价汇总、结果与通知、AI 写回）只读这张表，感知不到上游是协同文档。
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interview_evaluation")
public class InterviewEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 草稿
     */
    public static final int STATUS_DRAFT = 1;
    /**
     * 已提交
     */
    public static final int STATUS_SUBMITTED = 2;

    @TableId(value = "eval_id", type = IdType.AUTO)
    private Integer evalId;

    /**
     * 面试安排ID
     */
    @TableField("schedule_id")
    private Integer scheduleId;

    /**
     * 招募周期ID（冗余，便于按届查询）
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 简历ID（冗余）
     */
    @TableField("resume_id")
    private Integer resumeId;

    /**
     * 面试官用户ID
     */
    @TableField("interviewer_user_id")
    private Integer interviewerUserId;

    /**
     * 各维度得分，JSON：{dimensionId: score}
     */
    @TableField("scores")
    private String scores;

    /**
     * 按权重算好的加权总分（冗余，供汇总排序时不必解 JSON）
     */
    @TableField("total_score")
    private BigDecimal totalScore;

    /**
     * 评语
     */
    @TableField("comment")
    private String comment;

    /**
     * 推荐意见：1倾向通过 2待定 3不倾向
     */
    @TableField("recommendation")
    private Integer recommendation;

    /**
     * 1草稿 2已提交
     */
    @TableField("status")
    private Integer status;

    /**
     * AI 总评（建议分/理由/风险点），由方案二写入，仅供参考，不自动替代人的评分
     */
    @TableField("ai_suggestion")
    private String aiSuggestion;

    /**
     * 物化版本号（协同服务给出的单调值，通常为时间戳），用于丢弃迟到的旧快照
     */
    @TableField("version")
    private Long version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
