package club.boyuan.official.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PermissionDTO {
    private Integer permissionId;
    
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 50, message = "权限名称长度不能超过50个字符")
    private String permissionName;
    
    @NotBlank(message = "权限代码不能为空")
    @Size(max = 50, message = "权限代码长度不能超过50个字符")
    private String permissionCode;
    
    @Size(max = 100, message = "资源标识符长度不能超过100个字符")
    private String resourceIdentifier;
    
    @Size(max = 200, message = "权限描述长度不能超过200个字符")
    private String description;
    
    public PermissionDTO() {
    }
    
    @Override
    public String toString() {
        return "PermissionDTO{permissionId = " + permissionId + ", permissionName = " + permissionName + ", permissionCode = " + permissionCode + ", resourceIdentifier = " + resourceIdentifier + ", description = " + description + "}";
    }
}