package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class AuthLoginDTO {
    @NotBlank(message = "认证ID不能为空")
    private String auth_id;

    @NotBlank(message = "认证类型不能为空")
    @Pattern(regexp = "^(email-password|email-code|phone-password|phone-code|username-password)$", message = "不支持的认证方式")
    private String auth_type;

    @NotBlank(message = "验证信息不能为空")
    private String verify;

    public String toString() {
        return "AuthLoginDTO{auth_id = " + auth_id + ", auth_type = " + auth_type + ", verify = " + verify + "}";
    }
}