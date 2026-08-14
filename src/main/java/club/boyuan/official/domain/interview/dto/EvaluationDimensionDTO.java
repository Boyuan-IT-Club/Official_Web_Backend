package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分维度（协同评价表的一列）
 */
@Data
public class EvaluationDimensionDTO {

    private Integer dimensionId;

    private String name;

    /** 该维度满分 */
    private Integer maxScore;

    /** 加权总分中的权重 */
    private BigDecimal weight;

    /** 列顺序（升序） */
    private Integer sortOrder;
}
