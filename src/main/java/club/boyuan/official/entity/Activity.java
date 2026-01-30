package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 社团活动实体类
 * 用于管理社团活动相关信息
 */
@Entity
@Table(name = "activity")
@TableName("activity") // MyBatis-Plus 表映射注解
public class Activity {

    @Id
    @TableId(value = "activity_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Integer activityId;

    @Column(name = "title", nullable = false, length = 100)
    @TableField("title") // MyBatis-Plus 字段映射
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    @TableField("description")
    private String description;

    @Column(name = "category", length = 20)
    @TableField("category")
    private String category;

    @Column(name = "cover_image", length = 255)
    @TableField("cover_image")
    private String coverImage;

    @Column(name = "start_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("start_time")
    private LocalDate startTime;

    @Column(name = "end_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("end_time")
    private LocalDate endTime;

    @Column(name = "signup_start")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("signup_start")
    private LocalDate signupStart;

    @Column(name = "signup_deadline")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("signup_deadline")
    private LocalDate signupDeadline;

    @Column(name = "location", length = 100)
    @TableField("location")
    private String location;

    @Column(name = "max_participants", columnDefinition = "INT DEFAULT 0")
    @TableField("max_participants")
    private Integer maxParticipants;

    @Column(name = "current_participants", columnDefinition = "INT DEFAULT 0")
    @TableField("current_participants")
    private Integer currentParticipants;

    @Column(name = "status", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    @TableField("status")//活动状态（0-未开始，1-进行中，2-已结束，3-已取消）//
    private Integer status;

    @Column(name = "is_featured", columnDefinition = "TINYINT(1) DEFAULT 0")
    @TableField("is_featured")//标记推荐活动 1-推荐 0-默认//
    private Boolean isFeatured;

    @Column(name = "cycle_sequence", columnDefinition = "INT DEFAULT 0")
    @TableField("cycle_sequence")
    private Integer cycleSequence;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updatedAt;

    public Activity() {
    }

    public Activity(String title, String description, String category, LocalDate startTime, LocalDate endTime) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = 0; // 默认未开始
        this.currentParticipants = 0; // 默认没有参与者
        this.maxParticipants = 0; // 默认无限制
    }

    // Getter 和 Setter 方法
    public Integer getActivityId() {
        return activityId;
    }

    public void setActivityId(Integer activityId) {
        this.activityId = activityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public LocalDate getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDate startTime) {
        this.startTime = startTime;
    }

    public LocalDate getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDate endTime) {
        this.endTime = endTime;
    }

    public LocalDate getSignupStart() {
        return signupStart;
    }

    public void setSignupStart(LocalDate signupStart) {
        this.signupStart = signupStart;
    }

    public LocalDate getSignupDeadline() {
        return signupDeadline;
    }

    public void setSignupDeadline(LocalDate signupDeadline) {
        this.signupDeadline = signupDeadline;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public Integer getCurrentParticipants() {
        return currentParticipants;
    }

    public void setCurrentParticipants(Integer currentParticipants) {
        this.currentParticipants = currentParticipants;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Boolean isFeatured) {
        this.isFeatured = isFeatured;
    }

    public Integer getCycleSequence() {
        return cycleSequence;
    }

    public void setCycleSequence(Integer cycleSequence) {
        this.cycleSequence = cycleSequence;
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
