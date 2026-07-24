package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@TableName("user_role") // MyBatis-Plus 表映射注解
public class UserRole {
    @TableId(value = "user_role_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    private Integer userRoleId;

    @TableField("user_id") // MyBatis-Plus 字段映射
    private Integer userId;

    @TableField("role_id")
    private Integer roleId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    // 关联关系 - 标记为非数据库字段
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private User user;

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
