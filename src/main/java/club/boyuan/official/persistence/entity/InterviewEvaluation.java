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
 * 面试评价：一场面试一份，同场次的多位面试官共同编辑。
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
     * 进行中
     */
    public static final int STATUS_DRAFT = 1;
    /**
     * 已定稿
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
     * 各维度得分，JSON：{dimensionId: score}
     */
    @TableField("scores")
    private String scores;

    /** 各维度独立评语 {dimensionId: text}，JSON */
    @TableField("dimension_notes")
    private String dimensionNotes;

    /** 各维度评价的作者 {dimensionId: userId}，JSON。来源为协同服务的单元格级写入记录 */
    @TableField("dimension_writers")
    private String dimensionWriters;

    /**
     * 按权重算好的加权总分（冗余，供汇总排序时不必解 JSON）
     */
    @TableField("total_score")
    private BigDecimal totalScore;

    /**
     * 面试记录与评语（多位面试官共同编辑的结果）
     */
    @TableField("comment")
    private String comment;

    /**
     * 共同结论：1倾向通过 2待定 3不倾向
     */
    @TableField("recommendation")
    private Integer recommendation;

    /**
     * 1进行中 2已定稿
     */
    @TableField("status")
    private Integer status;

    /**
     * AI 总评（建议分/理由/风险点），由方案二写入，仅供参考，不自动替代人的评分
     */
    @TableField("ai_suggestion")
    private String aiSuggestion;

    /**
     * 参与编辑过的面试官 userId 列表，JSON 数组。
     * 由协同服务旁路记录、Java 侧校验场次绑定后落库，用于署名与追责。
     */
    @TableField("contributors")
    private String contributors;

    /**
     * 最后修改人 userId
     */
    @TableField("last_edited_by")
    private Integer lastEditedBy;

    /**
     * 点击定稿的面试官 userId
     */
    @TableField("submitted_by")
    private Integer submittedBy;

    /**
     * 定稿时间
     */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

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
