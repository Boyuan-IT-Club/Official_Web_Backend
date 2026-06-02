package club.boyuan.official.feishu;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 飞书多维表格一行记录（原始 fields + 解析后的业务字段）。
 */
public record FeishuBitableRecord(
        String recordId,
        JsonNode fields,
        String name,
        String assignedDept,
        Boolean interviewPassed,
        Boolean preselectPassed,
        Boolean adjustable,
        String decisionMakerName
) {
}
