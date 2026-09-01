package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 协同评价表的状态视图
 */
@Data
public class EvaluationBoardDTO {

    private Integer cycleId;

    /** 协同文档名，前端据此连接 /collab，如 eval-board:3 */
    private String docName;

    /** 是否已锁定（锁定后全员只读） */
    private Boolean locked;

    /** 待评价的候选人行数 */
    private Integer rowCount;

    /** 评分维度（列）数量 */
    private Integer dimensionCount;

    private LocalDateTime updatedAt;
}
