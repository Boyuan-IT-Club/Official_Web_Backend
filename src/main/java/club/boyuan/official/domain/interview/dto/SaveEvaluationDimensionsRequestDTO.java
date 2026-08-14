package club.boyuan.official.domain.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存某周期的评分维度模板（整表覆盖语义）
 */
@Data
public class SaveEvaluationDimensionsRequestDTO {

    @NotEmpty(message = "至少需要一个评分维度")
    @Valid
    private List<DimensionItem> dimensions;

    @Data
    public static class DimensionItem {

        /** 为空表示新增；有值表示更新已有维度 */
        private Integer dimensionId;

        @NotBlank(message = "维度名称不能为空")
        @Size(max = 50, message = "维度名称不能超过50个字符")
        private String name;

        @NotNull(message = "满分不能为空")
        @Min(value = 1, message = "满分至少为1")
        private Integer maxScore;

        @NotNull(message = "权重不能为空")
        @DecimalMin(value = "0.01", message = "权重必须大于0")
        private BigDecimal weight;

        private Integer sortOrder;
    }
}
