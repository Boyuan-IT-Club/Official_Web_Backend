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
    @DisplayName("未知类型（单选等）原样透传，不擅自改管理员建好的列")
    void unknownTypesPassThrough() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("是否调剂", "是");

        Map<String, Object> out = FeishuBitableClient.coerceRow(row, Map.of("是否调剂", SINGLE_SELECT));

        assertEquals("是", out.get("是否调剂"));
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
}
