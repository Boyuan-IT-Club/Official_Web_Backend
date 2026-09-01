package club.boyuan.official.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 批量录取结果。skipped 里是不属于该周期或已不存在的 resultId——
 * 逐条跳过而不是整批失败，避免一个脏 ID 让整批操作白做。
 */
@Data
@AllArgsConstructor
public class BatchDecisionResponseDTO {

    private int updated;

    private List<Integer> skipped;
}
