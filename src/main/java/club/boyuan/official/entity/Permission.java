package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "permission")
@TableName("permission") // MyBatis-Plus 表映射注解
public class Permission {
    @Id
    @TableId(value = "permission_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission_name", nullable = false)
    @TableField("permission_name") // MyBatis-Plus 字段映射
    private String permissionName;

    @Column(name = "permission_code", nullable = false, unique = true)
    @TableField("permission_code")
    private String permissionCode;

    @Column(name = "resource_identifier")
    @TableField("resource_identifier")
    private String resourceIdentifier;

    @Column(name = "description")
    @TableField("description")
    private String description;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;

    // 关联关系 - 标记为非数据库字段
    @ManyToMany(mappedBy = "permissions")
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private List<Role> roles;

    public Permission() {
    }

    public Permission(String permissionName, String permissionCode) {
        this.permissionName = permissionName;
        this.permissionCode = permissionCode;
    }

    // Getter 和 Setter 方法
    public Integer getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Integer permissionId) {
        this.permissionId = permissionId;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public String toString() {
        return "Permission{permissionId = " + permissionId + ", permissionName = " + permissionName + ", permissionCode = " + permissionCode + ", resourceIdentifier = " + resourceIdentifier + ", description = " + description + ", createTime = " + createTime + ", updateTime = " + updateTime + "}";
    }
}
