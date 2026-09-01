package club.boyuan.official.domain.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 报告单明文中的一个 task 结果(工具仓 src/types.ts TaskResult)。
 */
@Data
public class TaskResult {

    @JsonProperty("score")
    private Integer score;

    @JsonProperty("max_score")
    private Integer maxScore;

    @JsonProperty("test_results")
    private List<TestResult> testResults;
}