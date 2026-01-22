package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "user")
@TableName("user") // MyBatis-Plus 表映射注解
public class User {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    
    @Id
    @TableId(value = "user_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", nullable = false, unique = true)
    @TableField("username") // MyBatis-Plus 字段映射
    private String username;

    @Column(name = "password", nullable = false)
    @TableField("password")
    private String password;

    @Column(name = "role", nullable = false)
    @TableField("role") // MyBatis-Plus 字段映射
    private String role;

    @Column(name = "name")
    @TableField("name")
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    @TableField("email")
    private String email;

    @Column(name = "phone")
    @TableField("phone")
    private String phone;

    @Column(name = "major")
    @TableField("major")
    private String major;

    @Column(name = "github")
    @TableField("github")
    private String github;

    @Column(name = "dept")
    @TableField("dept")
    private String dept;

    @Column(name = "create_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    @Column(name = "status", columnDefinition = "TINYINT(1) DEFAULT 0")
    @TableField("status")
    private Integer status;

    @Column(name = "is_member", columnDefinition = "TINYINT(1) DEFAULT 0")
    @TableField("is_member")
    private Integer isMember;

    @Column(name = "avatar")
    @TableField("avatar")
    private String avatar;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @TableField("is_deleted")
    @TableLogic // MyBatis-Plus 逻辑删除注解
    private Integer isDeleted;

    @Column(name = "update_time", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;

    // 关联关系 - 标记为非数据库字段
    @ManyToOne
    @JoinColumn(name = "dept_id", referencedColumnName = "dept_id", insertable = false, updatable = false)
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Department department;

    @ManyToMany
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private List<Role> roles;

    public User() {
    }

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.status = 1; // 默认启用
        this.isDeleted = 0; // 默认未删除
    }

    // Getter 和 Setter 方法
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGithub() {
        return github;
    }

    public void setGithub(String github) {
        this.github = github;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public Integer getDeptId() {
        return deptId;
    }

    public void setDeptId(Integer deptId) {
        this.deptId = deptId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
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

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public String toString() {
        return "User{userId = " + userId + ", username = " + username + ", password = " + password + ", name = " + name + ", email = " + email + ", phone = " + phone + ", major = " + major + ", github = " + github + ", deptId = " + deptId + ", avatar = " + avatar + ", status = " + status + ", isDeleted = " + isDeleted + ", createTime = " + createTime + ", updateTime = " + updateTime + "}";
    }
}