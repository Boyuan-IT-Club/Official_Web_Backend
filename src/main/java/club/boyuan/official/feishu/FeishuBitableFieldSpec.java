package club.boyuan.official.feishu;

/**
 * 导出到飞书时需要保证存在的列定义。
 */
public record FeishuBitableFieldSpec(String fieldName, FeishuBitableFieldType type) {
}
