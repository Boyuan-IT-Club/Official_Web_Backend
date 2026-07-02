package club.boyuan.official.feishu;

/**
 * 飞书多维表格字段类型（见开放平台 app-table-field/create）。
 */
public enum FeishuBitableFieldType {

    TEXT(1),
    NUMBER(2),
    CHECKBOX(7),
    USER(11);

    private final int code;

    FeishuBitableFieldType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
