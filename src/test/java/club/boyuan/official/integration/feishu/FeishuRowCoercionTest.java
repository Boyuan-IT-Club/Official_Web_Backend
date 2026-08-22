package club.boyuan.official.integration.feishu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归保护:写入飞书前必须把值转成目标列能接受的类型。
 *
 * 线上事故链(两层,都实测过):
 *   1. 表里没有那些列 -> FieldNameNotFound(1254045),整批失败
 *   2. 自动建列后列是文本(type=1),而 resumeScore 是 int ->
 *      TextFieldConvFail(1254060),整批仍然失败
 *
 * 已在真实飞书表上验证:文本列写整数 85 报 1254060,写字符串 "85" 返回 code 0。
 * 纯函数,不需要 Spring 上下文与网络。
 */
class FeishuRowCoercionTest {

    private static final int TEXT = 1;
    private static final int NUMBER = 2;
    private static final int SINGLE_SELECT = 3;
    private static final int MULTI_SELECT = 4;
    private static final int CHECKBOX = 7;
    private static final int DATE = 5;

    @Test
    @DisplayName("文本列：整数被转成字符串（这正是线上 TextFieldConvFail 的原因）")
    void intBecomesStringForTextColumn() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("简历评分", 85);
        row.put("姓名", "张三");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("简历评分", TEXT, "姓名", TEXT));

        assertEquals("85", out.get("简历评分"), "文本列必须收到字符串，否则飞书报 TextFieldConvFail");
        assertEquals("张三", out.get("姓名"), "已经是字符串的值不该被改动");
    }

    @Test
    @DisplayName("文本列：null 转成空串，不是字面量 \"null\"")
    void nullBecomesEmptyStringNotLiteralNull() {
        Map<String, Object> row = new HashMap<>();
        row.put("自我介绍", null);

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("自我介绍", TEXT));

        assertEquals("", out.get("自我介绍"), "null 必须落成空串 —— 写 \"null\" 进飞书是脏数据");
    }

    @Test
    @DisplayName("数字列：管理员手动改成数字类型后，写数字而不是字符串")
    void numberColumnKeepsNumeric() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("简历评分", 85);

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("简历评分", NUMBER));

        assertTrue(out.get("简历评分") instanceof Number,
                "数字列要收到数字 —— 这是为了不打断管理员手动改类型的用法");
        assertEquals(85.0, ((Number) out.get("简历评分")).doubleValue(), 0.0001);
    }

    @Test
    @DisplayName("数字列：空值留空、非数字内容留空，而不是让整批失败")
    void numberColumnTolerantOfBadInput() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("空的", "");
        row.put("不是数字", "暂无");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("空的", NUMBER, "不是数字", NUMBER));

        assertNull(out.get("空的"), "空字符串不该写进数字列");
        assertNull(out.get("不是数字"), "非数字内容留空即可，不该整批 fail 掉其他人的行");
    }

    @Test
    @DisplayName("复选框：空值省略而不是写空串（线上 CheckboxFieldConvFail 的原因）")
    void checkboxEmptyIsOmitted() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("预选", "");
        row.put("是否调剂", "是");

        Map<String, Object> out = FeishuBitableClient.coerceRow(
                row, Map.of("预选", CHECKBOX, "是否调剂", CHECKBOX));

        assertFalse(out.containsKey("预选"),
                "空值必须整键省略 —— 往复选框写 \"\" 会让整批 CheckboxFieldConvFail");
        assertEquals(Boolean.TRUE, out.get("是否调剂"), "「是」应转成布尔 true");
    }

    @Test
    @DisplayName("多选：斜杠分隔的部门串转成数组")
    void multiSelectSplitsIntoArray() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("意向部门", "技术部 / 项目部");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("意向部门", MULTI_SELECT));

        assertEquals(java.util.List.of("技术部", "项目部"), out.get("意向部门"),
                "多选列只接受数组 —— formatIntendedDepartments 产出的是 \"甲 / 乙\"");
    }

    @Test
    @DisplayName("单选与日期：空值省略，非空透传")
    void singleSelectAndDateOmitEmpty() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("所属部门（教室）", "");
        row.put("面试日期", "");
        row.put("年级选项", "大一");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row,
                Map.of("所属部门（教室）", SINGLE_SELECT, "面试日期", DATE, "年级选项", SINGLE_SELECT));

        assertFalse(out.containsKey("所属部门（教室）"), "单选不接受空串");
        assertFalse(out.containsKey("面试日期"), "日期不接受空串");
        assertEquals("大一", out.get("年级选项"));
    }

    @Test
    @DisplayName("真实模板表的混合类型：一行全过，不漏不炸")
    void realTemplateMixedTypes() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("姓名", "丁华烨");
        row.put("意向部门", "技术部 / 项目部");
        row.put("简历评分", 85);
        row.put("预选", "");
        row.put("是否调剂", "");
        row.put("面试评价", "");

        // 类型取自社团真实模板表（实测）
        Map<String, Integer> types = Map.of(
                "姓名", TEXT, "意向部门", MULTI_SELECT, "简历评分", TEXT,
                "预选", CHECKBOX, "是否调剂", CHECKBOX, "面试评价", TEXT);

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, types);

        assertEquals("丁华烨", out.get("姓名"));
        assertEquals(java.util.List.of("技术部", "项目部"), out.get("意向部门"));
        assertEquals("85", out.get("简历评分"), "文本型评分列要收字符串");
        assertEquals("", out.get("面试评价"), "文本列的空值写空串是合法的");
        assertFalse(out.containsKey("预选"), "复选框空值省略");
        assertFalse(out.containsKey("是否调剂"), "复选框空值省略");
    }

    @Test
    @DisplayName("类型表缺失该列时按文本兜底 —— 兜底也必须转成字符串")
    void defaultsToTextWhenTypeUnknown() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("没在类型表里", 42);

        assertEquals("42", FeishuBitableClient.coerceRow(row, Map.of()).get("没在类型表里"));
        assertEquals("42", FeishuBitableClient.coerceRow(row, null).get("没在类型表里"),
                "类型表为 null 也不能崩，按文本兜底");
    }

    @Test
    @DisplayName("转换后必须保持列序 —— 自动建列按这个顺序建，乱序会建出乱序的表")
    void coercionPreservesColumnOrder() {
        // 与 buildRow 的 put 顺序一致：面试官从左到右填表的顺序
        java.util.List<String> expected = java.util.List.of(
                "姓名", "意向部门", "年级", "专业", "自我介绍",
                "第一类问题", "第二类问题", "第三类问题", "面试评价",
                "简历评分", "预选", "是否调剂", "记录人");

        Map<String, Object> row = new LinkedHashMap<>();
        expected.forEach(k -> row.put(k, "x"));

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of());

        assertEquals(expected, new java.util.ArrayList<>(out.keySet()),
                "coerceRow 用无序 Map 会打乱列序 —— 线上因此建出了「自我介绍」在最左、"
                        + "「姓名」在中间的表");
    }

    @Test
    @DisplayName("省略空值不该破坏其余列的顺序")
    void omissionKeepsRemainingOrder() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("姓名", "丁华烨");
        row.put("预选", "");          // 复选框空值 -> 省略
        row.put("年级", "大一");
        row.put("是否调剂", "");      // 复选框空值 -> 省略
        row.put("记录人", "");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row,
                Map.of("姓名", TEXT, "预选", CHECKBOX, "年级", TEXT,
                        "是否调剂", CHECKBOX, "记录人", TEXT));

        assertEquals(java.util.List.of("姓名", "年级", "记录人"),
                new java.util.ArrayList<>(out.keySet()),
                "被省略的键消失，其余键保持原有相对顺序");
    }
}
