package club.boyuan.official.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {
    // 这里刻意没有 username：它由后端从邮箱推导（= 11 位学号），不接受客户端传值。
    // 旧客户端仍会在请求体里带上这个字段，Jackson 默认忽略未知属性，不会报错。

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度必须在8-20个字符之间")
    private String password;

    /**
     * 注册邮箱：11 位学号 + @stu.ecnu.edu.cn。
     * <p>
     * 约束必须放在 DTO 上，不能只靠 Controller 里的 validateStudentEmail——
     * @Valid 的字段校验跑在方法体之前，而 username 取自邮箱前缀（前端
     * email.split('@')[0]，注册表单没有用户名输入框）。前缀短于 4 个字符时
     * username 的 @Size 先炸，用户看到的是「用户名长度必须在4-20个字符之间」，
     * 指向一个他根本没填过的字段，真正的原因（邮箱不对）反而说不出口。
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Pattern(regexp = "^\\d{11}@stu\\.ecnu\\.edu\\.cn$",
            message = "邮箱须为 11 位学号的学生邮箱（学号@stu.ecnu.edu.cn）")
    private String email;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 20, message = "确认密码长度必须在8-20个字符之间")
    private String confirmPassword;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 20, message = "姓名长度必须在2-20个字符之间")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    
    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;
}