package club.boyuan.official.integration.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 将飞书多维表格 fields 解析为 {@link FeishuBitableRecord}。
 */
public final class FeishuBitableRecordParser {

    private FeishuBitableRecordParser() {
    }

    public static FeishuBitableRecord parse(String recordId, JsonNode fields) {
        String name = FeishuFieldValueExtractor.extractText(fields, FeishuBitableColumns.NAME);
        String dept = FeishuFieldValueExtractor.extractText(fields, FeishuBitableColumns.ASSIGNED_DEPT);
        if (!StringUtils.hasText(dept)) {
            dept = FeishuFieldValueExtractor.extractText(fields, FeishuBitableColumns.DEPARTMENT);
        }
        if (!StringUtils.hasText(dept)) {
            dept = FeishuFieldValueExtractor.extractText(fields, FeishuBitableColumns.INTENDED_DEPT);
        }
        return new FeishuBitableRecord(
                recordId,
                fields,
                name,
                dept,
                FeishuFieldValueExtractor.extractCheckbox(fields, FeishuBitableColumns.INTERVIEW_PASSED),
                FeishuFieldValueExtractor.extractCheckbox(fields, FeishuBitableColumns.PRESELECT_PASSED),
                FeishuFieldValueExtractor.extractCheckbox(fields, FeishuBitableColumns.ADJUSTABLE),
                FeishuFieldValueExtractor.extractPersonName(fields, FeishuBitableColumns.DECISION_MAKER)
        );
    }
}
