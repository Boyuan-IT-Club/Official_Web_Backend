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
@Table(name = "department")
@TableName("department") // MyBatis-Plus 表映射注解
public class Department {
    @Id
    @TableId(value = "dept_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id")
    private Integer deptId;

    @Column(name = "dept_name", nullable = false)
    @TableField("dept_name") // MyBatis-Plus 字段映射
    private String deptName;

    @Column(name = "dept_code", nullable = false, unique = true)
    @TableField("dept_code")
    private String deptCode;

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
    @OneToMany(mappedBy = "department")
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
