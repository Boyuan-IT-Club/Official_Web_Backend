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
@Table(name = "role")
@TableName("role") // MyBatis-Plus 表映射注解
public class Role {
    @Id
    @TableId(value = "role_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name", nullable = false)
    @TableField("role_name") // MyBatis-Plus 字段映射
    private String roleName;

    @Column(name = "role_code", nullable = false, unique = true)
    @TableField("role_code")
    private String roleCode;

    @Column(name = "description")
    @TableField("description")
    private String description;

    @Column(name = "status", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    @TableField("status")
    private Integer status;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    @Column(name = "update_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;

    // 关联关系 - 标记为非数据库字段
    @ManyToMany(mappedBy = "roles")
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private List<User> users;

    @ManyToMany
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private List<Permission> permissions;

    public Role() {
    }

    public Role(String roleName, String roleCode) {
        this.roleName = roleName;
        this.roleCode = roleCode;
        this.status = 1; // 默认启用
    }

    // Getter 和 Setter 方法
    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

    public String toString() {
        return "Role{roleId = " + roleId + ", roleName = " + roleName + ", roleCode = " + roleCode + ", description = " + description + ", status = " + status + ", createTime = " + createTime + ", updateTime = " + updateTime + "}";
    }
}
