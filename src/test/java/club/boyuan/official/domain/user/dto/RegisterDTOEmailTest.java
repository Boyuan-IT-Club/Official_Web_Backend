package club.boyuan.official.domain.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 学号约束必须落在 DTO 上，不能只放在 Controller 方法体里。
 * <p>
 * 起因：@Valid 的字段校验跑在方法体之前，而 username 取自邮箱前缀
 * （前端 email.split('@')[0]，注册表单里没有用户名输入框）。前缀短于 4 个
 * 字符时 username 的 @Size 先炸，用户看到「用户名长度必须在4-20个字符之间」
 * ——指向一个他根本没填过的字段，真正的原因说不出口。线上实测过：
 * cr@stu.ecnu.edu.cn 返回的就是这句，Controller 里的 2022 根本没机会执行。
 */
class RegisterDTOEmailTest {

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    @Test
    @DisplayName("前缀短于 4 字符时，报错里必须点名邮箱，而不是只怪用户名")
    void shortPrefixStillBlamesEmail() {
        List<String> msgs = messages(validate("cr@stu.ecnu.edu.cn", "cr"));
        assertTrue(msgs.contains("邮箱须为 11 位学号的学生邮箱（学号@stu.ecnu.edu.cn）"),
                "用户名长度先炸时，邮箱这条原因也要一并给出，否则用户被指向一个没填过的字段；实际: " + msgs);
    }

    @Test
    @DisplayName("位数/后缀/非数字都拒")
    void rejectsBadEmails() {
        for (String bad : new String[]{
                "1024510@stu.ecnu.edu.cn", "102451014171@stu.ecnu.edu.cn",
                "10245101417@qq.com", "abcdefghijk@stu.ecnu.edu.cn"}) {
            assertTrue(messages(validate(bad, "10245101417")).stream().anyMatch(m -> m.contains("11 位学号")),
                    "应当拒绝: " + bad);
        }
    }

    @Test
    @DisplayName("合法学号邮箱不触发任何邮箱相关约束")
    void acceptsStudentEmail() {
        List<String> msgs = messages(validate("10245101417@stu.ecnu.edu.cn", "10245101417"));
        assertFalse(msgs.stream().anyMatch(m -> m.contains("邮箱")), "合法邮箱不该报错: " + msgs);
    }

    private static List<String> messages(Set<ConstraintViolation<RegisterDTO>> v) {
        return v.stream().map(ConstraintViolation::getMessage).toList();
    }

    private static Set<ConstraintViolation<RegisterDTO>> validate(String email, String username) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("Aa1!aaaa");
        dto.setConfirmPassword("Aa1!aaaa");
        dto.setName("测试");
        dto.setPhone("13800138000");
        dto.setEmailCode("000000");
        dto.setEmail(email);
        return VALIDATOR.validate(dto);
    }
}
