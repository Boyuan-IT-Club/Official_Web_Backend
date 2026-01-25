package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "role_permission")
@TableName("role_permission") // MyBatis-Plus 表映射注解
public class RolePermission {
    @Id
    @TableId(value = "role_permission_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_permission_id")
    private Integer rolePermissionId;

    @Column(name = "role_id", nullable = false)
    @TableField("role_id") // MyBatis-Plus 字段映射
    private Integer roleId;

    @Column(name = "permission_id", nullable = false)
    @TableField("permission_id")
    private Integer permissionId;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    // 关联关系 - 标记为非数据库字段
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id", insertable = false, updatable = false)
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Role role;

    @ManyToOne
    @JoinColumn(name = "permission_id", referencedColumnName = "permission_id", insertable = false, updatable = false)
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
