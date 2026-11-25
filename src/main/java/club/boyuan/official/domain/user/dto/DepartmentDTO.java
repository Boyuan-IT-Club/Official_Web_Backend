package club.boyuan.official.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DepartmentDTO {
    private Integer deptId;
    
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称长度不能超过50个字符")
    private String deptName;
    
    
    @Size(max = 200, message = "部门描述长度不能超过200个字符")
    private String description;
    
    private Integer status;
    
    public DepartmentDTO() {
    }
    
    @Override
    public String toString() {
        return "DepartmentDTO{deptId = " + deptId + ", deptName = " + deptName + ", description = " + description + ", status = " + status + "}";
    }
}