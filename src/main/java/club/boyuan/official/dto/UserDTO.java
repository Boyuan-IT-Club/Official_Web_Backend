package club.boyuan.official.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserDTO {
    private Integer userId;
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Length(min=6, max=16, message="密码长度必须在6-16之间")
    private String password;
    @Email(message = "邮箱格式不正确")
    @Pattern(regexp = ".+@stu\\.ecnu\\.edu\\.cn$" , message = "邮箱必须以@stu.ecnu.edu.cn结尾")
    private String email;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "电话不能为空")
    private String phone;
    @Pattern(regexp = "ADMIN|USER", message = "角色必须是ADMIN或USER")
    private String role;
    private boolean status;
    private String dept;

    public boolean getStatus() {
        return status;
    }

    public UserDTO() {
    }

    public String toString() {
        return "UserDTO{userId = " + userId + ", username = " + username + ", password = " + password + ", email = " + email + ", name = " + name + ", phone = " + phone + ", role = " + role + ", status = " + status + ", dept = " + dept + "}";
    }
}