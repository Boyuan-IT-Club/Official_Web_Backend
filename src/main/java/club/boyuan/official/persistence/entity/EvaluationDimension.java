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
 * 面试评分维度模板（每届可配置），协同评价表播种时据此生成初始列。
 * </p>
 *
 * @author dhy
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("evaluation_dimension")
public class EvaluationDimension implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "dimension_id", type = IdType.AUTO)
    private Integer dimensionId;

    /**
     * 招募周期ID
     */
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 维度名称，如「技术能力」
     */
    @TableField("name")
    private String name;

    /**
     * 该维度满分
     */
    @TableField("max_score")
    private Integer maxScore;

    /**
     * 加权总分中的权重
     */
    @TableField("weight")
    private BigDecimal weight;

    /**
     * 列顺序（升序）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
