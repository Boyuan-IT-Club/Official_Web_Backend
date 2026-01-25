package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RoleDTO {
    private Integer roleId;
    
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过50个字符")
    private String roleName;
    
    @NotBlank(message = "角色代码不能为空")
    @Size(max = 50, message = "角色代码长度不能超过50个字符")
    private String roleCode;
    
    @Size(max = 200, message = "角色描述长度不能超过200个字符")
    private String description;
    
    private Integer status;
    
    public RoleDTO() {
    }
    
    @Override
    public String toString() {
        return "RoleDTO{roleId = " + roleId + ", roleName = " + roleName + ", roleCode = " + roleCode + ", description = " + description + ", status = " + status + "}";
    }
}