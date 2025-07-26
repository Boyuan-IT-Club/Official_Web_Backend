package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String auth_type;
    @NotBlank(message = "验证信息不能为空")
    private String verify;
    public String toString() {
        return "AuthLoginDTO{auth_id = " + auth_id + ", auth_type = " + auth_type + ", verify = " + verify + "}";
    }
}