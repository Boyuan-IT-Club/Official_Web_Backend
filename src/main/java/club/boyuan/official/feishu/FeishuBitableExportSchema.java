package club.boyuan.official.feishu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台 → 飞书导出列定义与行组装（列名须与 {@link FeishuBitableColumns} 一致）。
 */
public final class FeishuBitableExportSchema {

    public static final List<FeishuBitableFieldSpec> PUSH_FIELDS = List.of(
            new FeishuBitableFieldSpec(FeishuBitableColumns.NAME, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.INTENDED_DEPT, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.GRADE, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.MAJOR, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.SELF_INTRO, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.QUESTION_ONE, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.QUESTION_TWO, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.QUESTION_THREE, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.EVALUATION, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.RESUME_SCORE, FeishuBitableFieldType.NUMBER),
            new FeishuBitableFieldSpec(FeishuBitableColumns.PRESELECT, FeishuBitableFieldType.TEXT),
            new FeishuBitableFieldSpec(FeishuBitableColumns.ADJUSTABLE, FeishuBitableFieldType.CHECKBOX),
            new FeishuBitableFieldSpec(FeishuBitableColumns.RECORDER, FeishuBitableFieldType.TEXT)
    );

    private FeishuBitableExportSchema() {
    }

    public static Map<String, Object> buildPushRow(ResumeFieldReader.ResumeSnapshot snapshot) {
        Map<String, Object> fields = new LinkedHashMap<>();
        putText(fields, FeishuBitableColumns.NAME, snapshot.name());
        putText(fields, FeishuBitableColumns.INTENDED_DEPT, snapshot.intendedDepartments());
        putText(fields, FeishuBitableColumns.GRADE, snapshot.grade());
        putText(fields, FeishuBitableColumns.MAJOR, snapshot.major());
        putText(fields, FeishuBitableColumns.SELF_INTRO, snapshot.selfIntroduction());
        putText(fields, FeishuBitableColumns.QUESTION_ONE, "");
        putText(fields, FeishuBitableColumns.QUESTION_TWO, "");
        putText(fields, FeishuBitableColumns.QUESTION_THREE, "");
        putText(fields, FeishuBitableColumns.EVALUATION, "");
        fields.put(FeishuBitableColumns.RESUME_SCORE, snapshot.resumeScore());
        putText(fields, FeishuBitableColumns.PRESELECT, "");
        fields.put(FeishuBitableColumns.ADJUSTABLE, false);
        putText(fields, FeishuBitableColumns.RECORDER, "");
        return fields;
    }

    private static void putText(Map<String, Object> fields, String key, String value) {
        fields.put(key, value == null ? "" : value);
    }
}
