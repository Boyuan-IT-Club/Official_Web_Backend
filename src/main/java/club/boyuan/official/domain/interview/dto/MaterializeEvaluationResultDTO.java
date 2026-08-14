package club.boyuan.official.domain.interview.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物化回写结果，协同服务据此判断是否需要告警。
 */
@Data
public class MaterializeEvaluationResultDTO {

    /** 成功写入的条目数 */
    private int accepted;

    /** 被丢弃的条目数（越权或行不属于本周期） */
    private int rejected;

    /** 被丢弃条目的说明，便于排查 */
    private List<String> rejectReasons = new ArrayList<>();
}
