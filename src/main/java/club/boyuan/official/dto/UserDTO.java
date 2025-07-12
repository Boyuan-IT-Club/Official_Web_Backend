package club.boyuan.official.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public class UserDTO {
    private Integer userId;

    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Length(min=6, max=16, message="密码长度必须在6-16之间")
    private String password;
    @Email(message = "邮箱格式不正确")
    @Pattern(regexp = ".+@stu\\.ecnu\\.edu$" , message = "邮箱必须以@stu.ecnu.edu结尾")
    private String email;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "电话不能为空")
    private String phone;
    @Pattern(regexp = "ADMIN|USER", message = "角色必须是ADMIN或USER")
    private String role;
    private boolean status;
    private String dept;

    public UserDTO(String username, String password, String email, String name, String phone, boolean status) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.status = status;
    }

    public UserDTO(Integer userId, String username, String password, String email, String name, String phone, String role, boolean status, String dept) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.dept = dept;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public boolean getStatus() {
        return status;
    }

    public UserDTO() {
    }

    public UserDTO(Integer userId, String username, String password, String email, String name, String phone) {
        this(userId, username, password, email, name, phone, "USER");
    }

    public UserDTO(Integer userId, String username, String password, String email, String name, String phone, String role) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
    }

    /**
     * 获取
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * 获取
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * 获取
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取
     * @return phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置
     * @param phone
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取
     * @return role
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置
     * @param role
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取
     * @return userId
     */
    public Integer getUserId() {
        return userId;
    }

    /**
     * 设置
     * @param userId
     */
    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    /**
     * 设置
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 设置
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 设置
     * @param email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取
     * @return status
     */
    public boolean isStatus() {
        return status;
    }

    /**
     * 获取
     * @return dept
     */
    public String getDept() {
        return dept;
    }

    /**
     * 设置
     * @param dept
     */
    public void setDept(String dept) {
        this.dept = dept;
    }

    public String toString() {
        return "UserDTO{userId = " + userId + ", username = " + username + ", password = " + password + ", email = " + email + ", name = " + name + ", phone = " + phone + ", role = " + role + ", status = " + status + ", dept = " + dept + "}";
    }
}