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
@Table(name = "user_role")
@TableName("user_role") // MyBatis-Plus 表映射注解
public class UserRole {
    @Id
    @TableId(value = "user_role_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_role_id")
    private Integer userRoleId;

    @Column(name = "user_id", nullable = false)
    @TableField("user_id") // MyBatis-Plus 字段映射
    private Integer userId;

    @Column(name = "role_id", nullable = false)
    @TableField("role_id")
    private Integer roleId;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    // 关联关系 - 标记为非数据库字段
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private User user;

    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id", insertable = false, updatable = false)
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Role role;

    public UserRole() {
    }

    public UserRole(Integer userId, Integer roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    // Getter 和 Setter 方法
    public Integer getUserRoleId() {
        return userRoleId;
    }

    public void setUserRoleId(Integer userRoleId) {
        this.userRoleId = userRoleId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String toString() {
        return "UserRole{userRoleId = " + userRoleId + ", userId = " + userId + ", roleId = " + roleId + ", createTime = " + createTime + "}";
    }
}
