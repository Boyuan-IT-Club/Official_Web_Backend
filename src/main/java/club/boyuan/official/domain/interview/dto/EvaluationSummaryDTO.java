package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全周期评价汇总：候选人 × 面试官矩阵，供「结果与通知」录入录取决定时参考。
 * 读的是物化后的 interview_evaluation，与协同文档无关。
 */
@Data
public class EvaluationSummaryDTO {

    private Integer cycleId;

    /** 列定义，供前端渲染各维度均分 */
    private List<EvaluationDimensionDTO> dimensions = new ArrayList<>();

    private List<CandidateSummary> candidates = new ArrayList<>();

    @Data
    public static class CandidateSummary {

        private Integer scheduleId;

        private Integer resumeId;

        private Integer userId;

        private String candidateName;

        private String deptName;

        private LocalDateTime interviewTime;

        /** 各维度在所有面试官间的均分 {dimensionId: avgScore} */
        private Map<Integer, BigDecimal> dimensionAverages = new LinkedHashMap<>();

        /** 该候选人所有面试官加权总分的均值 */
        private BigDecimal averageTotalScore;

        /** 推荐意见分布 {1倾向通过, 2待定, 3不倾向} → 人数 */
        private Map<Integer, Integer> recommendationCounts = new LinkedHashMap<>();

        /** 已提交的评价数 / 应有的评价数 */
        private int submittedCount;

        private int expectedCount;

        /** 每位面试官的明细 */
        private List<InterviewerEvaluation> evaluations = new ArrayList<>();
    }

    @Data
    public static class InterviewerEvaluation {

        private Integer interviewerUserId;

        private String interviewerName;

        private Map<Integer, BigDecimal> scores = new LinkedHashMap<>();

        private BigDecimal totalScore;

        private String comment;

        private Integer recommendation;

        /** 1草稿 2已提交 */
        private Integer status;
    }
}
