package club.boyuan.official.domain.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 报告单明文(工具仓 src/types.ts Report)。
 */
@Data
public class Report {

    @JsonProperty("author")
    private String author;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("tasks")
    private Map<String, TaskResult> tasks;

    @JsonProperty("total_score")
    private Integer totalScore;

    /** 满分:四个 task 的 max_score 之和,缺省 400。 */
    public int computeMaxScore() {
        int max = 0;
        if (tasks != null) {
            for (TaskResult t : tasks.values()) {
                if (t != null && t.getMaxScore() != null) {
                    max += t.getMaxScore();
                }
            }
        }
        return max > 0 ? max : 400;
    }

    /** 按 task id 取值,缺省 0。 */
    public int taskScore(String id) {
        if (tasks == null || tasks.get(id) == null || tasks.get(id).getScore() == null) {
            return 0;
        }
        return tasks.get(id).getScore();
    }
}