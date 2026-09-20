package club.boyuan.official.common.utils;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 注册邮箱必须是「11 位学号 + @stu.ecnu.edu.cn」。
 * <p>
 * 2026-09 发现的缺口：此前后端只查后缀，cr@stu.ecnu.edu.cn 能一路走到建用户，
 * 再因为用户名取自邮箱前缀、只有 2 个字符而撞上 4-20 的长度限制，报出一句
 * 文不对题的「用户名长度必须在4-20个字符之间」。11 位约束当时只存在于前端，
 * 直接打 API 就能绕过。
 */
class StudentEmailValidationTest {

    private final MessageUtils messageUtils = new MessageUtils();

    @Test
    @DisplayName("11 位学号邮箱放行")
    void acceptsStudentEmail() {
        assertDoesNotThrow(() -> messageUtils.validateStudentEmail("10245101417@stu.ecnu.edu.cn"));
    }

    @Test
    @DisplayName("前缀不是数字就拒——就是 cr@ 那个 case")
    void rejectsNonNumericPrefix() {
        assertCode("cr@stu.ecnu.edu.cn");
        assertCode("dinghuaye@stu.ecnu.edu.cn");
    }

    @Test
    @DisplayName("位数不对就拒，多一位少一位都不行")
    void rejectsWrongLength() {
        assertCode("1024510@stu.ecnu.edu.cn");
        assertCode("102451014171@stu.ecnu.edu.cn");
    }

    @Test
    @DisplayName("后缀不对就拒，别的学校/个人邮箱都不行")
    void rejectsWrongSuffix() {
        assertCode("10245101417@qq.com");
        assertCode("10245101417@ecnu.edu.cn");
        assertCode("10245101417@stu.ecnu.edu.cn.evil.com");
    }

    @Test
    @DisplayName("空值不抛 NPE，按格式错处理")
    void rejectsBlank() {
        assertCode(null);
        assertCode("");
    }

    @Test
    @DisplayName("两侧空格不算错——粘贴很常见")
    void trimsWhitespace() {
        assertDoesNotThrow(() -> messageUtils.validateStudentEmail("  10245101417@stu.ecnu.edu.cn  "));
    }

    private void assertCode(String email) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> messageUtils.validateStudentEmail(email), "应当拒绝: " + email);
        assertEquals(BusinessExceptionEnum.INVALID_STUDENT_EMAIL.getCode(), ex.getCode());
    }
}
