package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全周期评价汇总：一位候选人一份评价，供「结果与通知」录入录取决定时参考。
 * 读的是物化后的 interview_evaluation，与协同文档无关。
 */
@Data
public class EvaluationSummaryDTO {

    private Integer cycleId;

    /** 列定义，供前端渲染各维度得分 */
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

        /** 各维度得分 {dimensionId: score}，共编模型下一位候选人只有一份，不再是均分 */
        private Map<Integer, BigDecimal> scores = new LinkedHashMap<>();

        private BigDecimal totalScore;

        /** 面试记录与评语 */
        private String comment;

        /** 共同结论：1倾向通过 2待定 3不倾向 */
        private Integer recommendation;

        /** 1进行中 2已定稿；尚无评价时为空 */
        private Integer status;

        /** 参与编辑过的面试官，用于署名 */
        private List<Contributor> contributors = new ArrayList<>();

        /** 该场次绑定的面试官数，供前端提示「几个人里已有几个动过」 */
        private int assignedInterviewerCount;

        private Integer lastEditedBy;

        private String lastEditedByName;

        private Integer submittedBy;

        private String submittedByName;

        private LocalDateTime submittedAt;
    }

    @Data
    public static class Contributor {

        private Integer userId;

        private String name;
    }
}
