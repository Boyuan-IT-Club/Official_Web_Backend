package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@TableName("role_permission") // MyBatis-Plus 表映射注解
public class RolePermission {
    @TableId(value = "role_permission_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    private Integer rolePermissionId;

    @TableField("role_id") // MyBatis-Plus 字段映射
    private Integer roleId;

    @TableField("permission_id")
    private Integer permissionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    // 关联关系 - 标记为非数据库字段
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Role role;

    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Permission permission;

    public RolePermission() {
    }

    public RolePermission(Integer roleId, Integer permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    // Getter 和 Setter 方法
    public Integer getRolePermissionId() {
        return rolePermissionId;
    }

    public void setRolePermissionId(Integer rolePermissionId) {
        this.rolePermissionId = rolePermissionId;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public Integer getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Integer permissionId) {
        this.permissionId = permissionId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public String toString() {
        return "RolePermission{rolePermissionId = " + rolePermissionId + ", roleId = " + roleId + ", permissionId = " + permissionId + ", createTime = " + createTime + "}";
    }
}
