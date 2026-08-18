package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@TableName("user") // MyBatis-Plus 表映射注解
public class User {
    @TableId(value = "user_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    private Integer userId;

    @TableField("username") // MyBatis-Plus 字段映射
    private String username;

    /**
     * 密码哈希。
     *
     * WRITE_ONLY:可以从 JSON 反序列化进来,但绝不序列化出去。
     * 在此之前 GET /api/admin/users 会把全部用户的 bcrypt 哈希原样返回
     * (列表 SQL 明确 SELECT 了 password,实体又没有任何 Json 注解),
     * 任何能打开「用户与角色」页的账号都能从接口响应里拿到全站哈希。
     * 用 WRITE_ONLY 而非 @JsonIgnore,以免影响可能存在的反序列化调用方。
     */
    @TableField("password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @TableField("name")
    private String name;

    @TableField("email")
    private String email;

    @TableField("phone")
    private String phone;

    @TableField("role")
    private String role;

    @TableField("major")
    private String major;

    @TableField("github")
    private String github;

    @TableField("dept_id")
    private Integer deptId;

    /**
     * 社员所属部门名称。
     *
     * 与 deptId 并存是历史结果：dept_id 是 V6 建表时的外键，而 dept(varchar)
     * 是后来手工加到生产库的，管理端的批量分配部门、列表查询与筛选全走 dept。
     * 两者未做同步，改动这块前先确认调用方读的是哪一个。
     */
    @TableField("dept")
    private String dept;

    /**
     * 是否已录取为社员。
     *
     * 用 getIsMember() 而非 isMember()：Jackson 对 isXxx() 形式的读方法会把
     * 属性名截成 member，前端读的是 isMember，键名一错就恒为 undefined
     * ——表现就是「部门」列永远显示非社员。
     */
    @TableField("is_member")
    private Boolean isMember;

    @TableField("avatar")
    private String avatar;

    @TableField("status")
    private Integer status;

    @TableField("is_deleted")
    @TableLogic // MyBatis-Plus 逻辑删除注解
    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;

    // 关联关系 - 标记为非数据库字段
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private Department department;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public Integer getDeptId() {
        return deptId;
    }

    public void setDeptId(Integer deptId) {
        this.deptId = deptId;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public Boolean getIsMember() {
        return isMember;
    }

    public void setIsMember(Boolean isMember) {
        this.isMember = isMember;
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