package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@TableName("department") // MyBatis-Plus 表映射注解
public class Department {
    @TableId(value = "dept_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    private Integer deptId;

    @TableField("dept_name") // MyBatis-Plus 字段映射
    private String deptName;

    @TableField("dept_code")
    private String deptCode;

    @TableField("description")
    private String description;

    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updateTime;

    // 关联关系 - 标记为非数据库字段
    @TableField(exist = false) // 标记为非数据库字段，避免 MyBatis-Plus 报错
    private List<User> users;

    public Department() {
    }

    public Department(String deptName, String deptCode) {
        this.deptName = deptName;
        this.deptCode = deptCode;
        this.status = 1; // 默认启用
    }

    // Getter 和 Setter 方法
    public Integer getDeptId() {
        return deptId;
    }

    public void setDeptId(Integer deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
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

    public String toString() {
        return "Department{deptId = " + deptId + ", deptName = " + deptName + ", deptCode = " + deptCode + ", description = " + description + ", status = " + status + ", createTime = " + createTime + ", updateTime = " + updateTime + "}";
    }
}
