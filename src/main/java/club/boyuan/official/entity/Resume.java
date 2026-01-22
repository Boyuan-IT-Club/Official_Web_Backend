package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume")
@TableName("resume") // MyBatis-Plus 表映射注解
public class Resume {
    @Id
    @TableId(value = "resume_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resume_id")
    private Integer resumeId;

    @Column(name = "user_id", nullable = false)
    @TableField("user_id") // MyBatis-Plus 字段映射
    private Integer userId;

    @Column(name = "cycle_id", nullable = false)
    @TableField("cycle_id")
    private Integer cycleId;

    /**
     * 简历状态:
     * 1 - 草稿
     * 2 - 已提交
     * 3 - 评审中
     * 4 - 通过
     * 5 - 未通过
     */
    @Column(name = "status", columnDefinition = "tinyint")
    @TableField("status")
    private Integer status;

    @Column(name = "submitted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updatedAt;

    // 构造函数
    public Resume() {
    }

    public Resume(Integer userId, Integer cycleId, Integer status) {
        this.userId = userId;
        this.cycleId = cycleId;
        this.status = status;
    }

    // Getter 和 Setter 方法
    public Integer getResumeId() {
        return resumeId;
    }

    public void setResumeId(Integer resumeId) {
        this.resumeId = resumeId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCycleId() {
        return cycleId;
    }

    public void setCycleId(Integer cycleId) {
        this.cycleId = cycleId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}