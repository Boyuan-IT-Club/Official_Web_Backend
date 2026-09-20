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
 * 注册请求体的字段约束。
 * <p>
 * 这组断言记录了一段两次才修对的历史：
 * <ol>
 *   <li>最初后端只查邮箱后缀，cr@stu.ecnu.edu.cn 能一路走到建用户；</li>
 *   <li>补了 Controller 里的校验，但 @Valid 跑在方法体之前，username 的
 *       @Size 先炸，用户看到的仍是「用户名长度必须在4-20个字符之间」
 *       ——指向一个注册表单里根本不存在的输入框；</li>
 *   <li>约束下沉到 DTO，报错里终于点得到邮箱；</li>
 *   <li>最后把 username 整个从请求体里拿掉，由后端从邮箱推导。
 *       错位的根源是「同一个值由前端算、后端独立校验」，去掉它才算修完。</li>
 * </ol>
 */
class RegisterDTOEmailTest {

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    @Test
    @DisplayName("邮箱不合规时只说邮箱，不再冒出用户名相关的提示")
    void badEmailOnlyBlamesEmail() {
        List<String> msgs = messages(validate("cr@stu.ecnu.edu.cn"));
        assertTrue(msgs.contains("邮箱须为 11 位学号的学生邮箱（学号@stu.ecnu.edu.cn）"),
                "应当点名邮箱；实际: " + msgs);
        assertFalse(msgs.stream().anyMatch(m -> m.contains("用户名")),
                "username 已不在请求体里，不该再有它的报错；实际: " + msgs);
    }

    @Test
    @DisplayName("位数/后缀/非数字都拒")
    void rejectsBadEmails() {
        for (String bad : new String[]{
                "1024510@stu.ecnu.edu.cn", "102451014171@stu.ecnu.edu.cn",
                "10245101417@qq.com", "abcdefghijk@stu.ecnu.edu.cn"}) {
            assertTrue(messages(validate(bad)).stream().anyMatch(m -> m.contains("11 位学号")),
                    "应当拒绝: " + bad);
        }
    }

    @Test
    @DisplayName("合法学号邮箱不触发任何约束")
    void acceptsStudentEmail() {
        List<String> msgs = messages(validate("10245101417@stu.ecnu.edu.cn"));
        assertTrue(msgs.isEmpty(), "合法载荷不该有任何违规: " + msgs);
    }

    private static List<String> messages(Set<ConstraintViolation<RegisterDTO>> v) {
        return v.stream().map(ConstraintViolation::getMessage).toList();
    }

    private static Set<ConstraintViolation<RegisterDTO>> validate(String email) {
        RegisterDTO dto = new RegisterDTO();
        dto.setPassword("Aa1!aaaa");
        dto.setConfirmPassword("Aa1!aaaa");
        dto.setName("测试");
        dto.setPhone("13800138000");
        dto.setEmailCode("000000");
        dto.setEmail(email);
        return VALIDATOR.validate(dto);
    }
}
