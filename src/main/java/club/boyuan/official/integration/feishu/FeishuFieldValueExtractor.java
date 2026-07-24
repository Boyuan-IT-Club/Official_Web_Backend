package club.boyuan.official.integration.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 从飞书多维表格 fields 节点解析单元格文本（兼容文本、单选、人员等常见类型）。
 */
public final class FeishuFieldValueExtractor {

    private FeishuFieldValueExtractor() {
    }

    /**
     * 解析复选框：true / false；缺省返回 null 表示未填写。
     */
    public static Boolean extractCheckbox(JsonNode fields, String columnName) {
        if (fields == null || !StringUtils.hasText(columnName)) {
            return null;
        }
        JsonNode cell = fields.get(columnName);
        if (cell == null || cell.isNull()) {
            return null;
        }
        if (cell.isBoolean()) {
            return cell.asBoolean();
        }
        if (cell.isNumber()) {
            return cell.asInt() != 0;
        }
        if (cell.isTextual()) {
            String t = cell.asText().trim();
            if (!StringUtils.hasText(t)) {
                return null;
            }
            if ("true".equalsIgnoreCase(t) || "是".equals(t) || "1".equals(t) || "通过".equals(t)) {
                return true;
            }
            if ("false".equalsIgnoreCase(t) || "否".equals(t) || "0".equals(t) || "不通过".equals(t)) {
                return false;
            }
            return null;
        }
        if (cell.isArray()) {
            if (cell.isEmpty()) {
                return false;
            }
            for (JsonNode item : cell) {
                Boolean b = extractCheckboxFromNode(item);
                if (b != null) {
                    return b;
                }
            }
            return true;
        }
        if (cell.isObject()) {
            return extractCheckboxFromNode(cell);
        }
        return null;
    }

    private static Boolean extractCheckboxFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.has("checked")) {
            return node.get("checked").asBoolean();
        }
        if (node.has("value") && node.get("value").isBoolean()) {
            return node.get("value").asBoolean();
        }
        String text = extractTextFromNode(node);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "是".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "否".equals(text)) {
            return false;
        }
        return null;
    }

    /**
     * 解析人员列（@提及），返回第一个人员的姓名。
     */
    public static String extractPersonName(JsonNode fields, String columnName) {
        if (fields == null || !StringUtils.hasText(columnName)) {
            return null;
        }
        JsonNode cell = fields.get(columnName);
        if (cell == null || cell.isNull()) {
            return null;
        }
        if (cell.isArray()) {
            for (JsonNode item : cell) {
                String name = personNameFromNode(item);
                if (StringUtils.hasText(name)) {
                    return name;
                }
            }
            return null;
        }
        if (cell.isObject()) {
            return personNameFromNode(cell);
        }
        String text = extractText(fields, columnName);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.startsWith("@")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    private static String personNameFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.has("name") && node.get("name").isTextual()) {
            return node.get("name").asText().trim();
        }
        if (node.has("text") && node.get("text").isTextual()) {
            String t = node.get("text").asText().trim();
            if (t.startsWith("@")) {
                t = t.substring(1).trim();
            }
            return t;
        }
        return null;
    }

    public static String extractText(JsonNode fields, String columnName) {
        if (fields == null || !StringUtils.hasText(columnName)) {
            return null;
        }
        JsonNode cell = fields.get(columnName);
        if (cell == null || cell.isNull()) {
            return null;
        }
        return extractTextFromNode(cell);
    }

    private static String extractTextFromNode(JsonNode cell) {
        return normalizeCell(cell);
    }

    private static String normalizeCell(JsonNode cell) {
        if (cell.isTextual() || cell.isNumber() || cell.isBoolean()) {
            return cell.asText().trim();
        }
        if (cell.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : cell) {
                String part = normalizeCell(item);
                if (StringUtils.hasText(part)) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(part);
                }
            }
            return sb.toString().trim();
        }
        if (cell.isObject()) {
            if (cell.has("text")) {
                return cell.get("text").asText("").trim();
            }
            if (cell.has("name")) {
                return cell.get("name").asText("").trim();
            }
            if (cell.has("value")) {
                return normalizeCell(cell.get("value"));
            }
        }
        return null;
    }
}
