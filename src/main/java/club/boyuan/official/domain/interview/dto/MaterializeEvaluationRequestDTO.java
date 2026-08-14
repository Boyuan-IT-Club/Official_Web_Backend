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

        /** 这条评价归属的面试官，取自单元格键 '<colId>:<userId>' 中的 userId */
        @NotNull(message = "面试官ID不能为空")
        private Integer interviewerUserId;

        /**
         * 实际写入这些单元格的用户（Yjs update 的 origin）。
         * 与 interviewerUserId 不一致意味着有人写了别人的格子，服务端据此丢弃并记审计。
         */
        @NotNull(message = "变更发起人不能为空")
        private Integer originUserId;

        /** 各维度得分 {dimensionId: score} */
        private Map<Integer, BigDecimal> scores;

        private String comment;

        /** 1倾向通过 2待定 3不倾向 */
        private Integer recommendation;

        /** 1草稿 2已提交，为空按草稿处理 */
        private Integer status;

        /** 单调递增的物化版本，用于丢弃迟到的旧快照 */
        @NotNull(message = "版本号不能为空")
        private Long version;
    }
}
