package club.boyuan.official.domain.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 协同服务把 Y.Doc 解析后的评价数据回写业务库。
 * 由协同服务防抖调用（默认 30s）或面试官点「提交」时即刻调用。
 */
@Data
public class MaterializeEvaluationRequestDTO {

    /** 来源协同文档名，用于审计与周期校验，如 eval-board:3 */
    @NotBlank(message = "文档名不能为空")
    private String docName;

    @NotNull(message = "周期ID不能为空")
    private Integer cycleId;

    @NotEmpty(message = "物化数据不能为空")
    @Valid
    private List<EvaluationItem> items;

    @Data
    public static class EvaluationItem {

        @NotNull(message = "面试安排ID不能为空")
        private Integer scheduleId;

        /**
         * 动过这一行的所有面试官（Yjs update 的 origin，由协同服务旁路记录）。
         * 服务端逐个校验是否绑定在该行所属场次上，未绑定者剔除并记审计。
         */
        private List<Integer> contributors;

        /** 最后一次改动这一行的人 */
        private Integer lastEditedBy;

        /** 把状态改为「已定稿」的那个人 */
        private Integer submittedBy;

        /** 各维度得分 {dimensionId: score} */
        private Map<Integer, BigDecimal> scores;

        /** 面试记录与评语 */
        private String comment;

        /** 共同结论：1倾向通过 2待定 3不倾向 */
        private Integer recommendation;

        /** 1进行中 2已定稿，为空按进行中处理 */
        private Integer status;

        /** 单调递增的物化版本，用于丢弃迟到的旧快照 */
        @NotNull(message = "版本号不能为空")
        private Long version;
    }
}
