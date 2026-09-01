package club.boyuan.official.domain.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 报告单明文中的一个检查项(工具仓 src/types.ts TestResult)。
 */
@Data
public class TestResult {

    @JsonProperty("name")
    private String name;

    @JsonProperty("passed")
    private Boolean passed;

    @JsonProperty("points")
    private Integer points;
}