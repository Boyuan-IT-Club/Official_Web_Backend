package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginDTO {
    @NotBlank(message = "认证ID不能为空")
    private String auth_id;

    @NotBlank(message = "认证类型不能为空")
    private String auth_type;

    @NotBlank(message = "验证信息不能为空")
    private String verify;

    public AuthLoginDTO() {
    }

    public AuthLoginDTO(String auth_id, String auth_type, String verify) {
        this.auth_id = auth_id;
        this.auth_type = auth_type;
        this.verify = verify;
    }

    /**
     * 获取
     * @return auth_id
     */
    public String getAuth_id() {
        return auth_id;
    }

    /**
     * 设置
     * @param auth_id
     */
    public void setAuth_id(String auth_id) {
        this.auth_id = auth_id;
    }

    /**
     * 获取
     * @return auth_type
     */
    public String getAuth_type() {
        return auth_type;
    }

    /**
     * 设置
     * @param auth_type
     */
    public void setAuth_type(String auth_type) {
        this.auth_type = auth_type;
    }

    /**
     * 获取
     * @return verify
     */
    public String getVerify() {
        return verify;
    }

    /**
     * 设置
     * @param verify
     */
    public void setVerify(String verify) {
        this.verify = verify;
    }

    public String toString() {
        return "AuthLoginDTO{auth_id = " + auth_id + ", auth_type = " + auth_type + ", verify = " + verify + "}";
    }
}