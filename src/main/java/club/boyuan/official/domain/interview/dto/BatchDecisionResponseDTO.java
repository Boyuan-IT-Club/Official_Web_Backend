package club.boyuan.official.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 批量录入结果的返回。
 *
 * skipped：没有被改动的 resultId（不属于该周期、已不存在、已有结果、在预录取名单里、初筛未通过）。
 * 逐条跳过而不是整批失败，避免一个脏 ID 让整批操作白做。
 * skippedDecided / skippedInPreAdmission 是其中两类的人数，前端据此如实说明「为什么没改」。
 */
@Data
@AllArgsConstructor
public class BatchDecisionResponseDTO {

    private int updated;

    private List<Integer> skipped;

    /** 已有结果（通过/未通过）而被跳过的人数：已定的结果不能被批量覆盖，只能逐个更正 */
    private int skippedDecided;

    /** 在预录取名单里而被跳过的人数：请在预录取页处理 */
    private int skippedInPreAdmission;

    public BatchDecisionResponseDTO(int updated, List<Integer> skipped) {
        this(updated, skipped, 0, 0);
    }
}
