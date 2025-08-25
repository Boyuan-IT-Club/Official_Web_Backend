package club.boyuan.official.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招募周期实体类
 * 用于管理社团的招募活动周期
 */
@Entity
@Table(name = "recruitment_cycle")
public class RecruitmentCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cycle_id")
    private Integer cycleId;

    @Column(name = "cycle_name", nullable = false)
    private String cycleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    /**
     * 招募活动状态:
     * 1 - 未开始
     * 2 - 进行中
     * 3 - 已结束
     * 4 - 已关闭
     */
    @Column(name = "status", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /**
     * 是否启用:
     * 0 - 禁用
     * 1 - 启用
     */
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Integer isActive;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // 构造函数
    public RecruitmentCycle() {
    }

    public RecruitmentCycle(String cycleName, String description, LocalDate startDate, LocalDate endDate, String academicYear) {
        this.cycleName = cycleName;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.academicYear = academicYear;
    }

    // Getter 和 Setter 方法
    public Integer getCycleId() {
        return cycleId;
    }

    public void setCycleId(Integer cycleId) {
        this.cycleId = cycleId;
    }

    public String getCycleName() {
        return cycleName;
    }

    public void setCycleName(String cycleName) {
        this.cycleName = cycleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
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

    /**
     * 根据当前日期自动更新状态
     * @param currentDate 当前日期
     */
    public void updateStatusBasedOnDate(LocalDate currentDate) {
        if (this.startDate != null && this.endDate != null) {
            // 如果当前日期早于开始日期，则状态为未开始
            if (currentDate.isBefore(this.startDate)) {
                this.status = 1; // 未开始
            }
            // 如果当前日期在开始日期和结束日期之间，则状态为进行中
            else if (!currentDate.isAfter(this.endDate) && !currentDate.isBefore(this.startDate)) {
                this.status = 2; // 进行中
            }
            // 如果当前日期晚于结束日期，则状态为已结束
            else if (currentDate.isAfter(this.endDate)) {
                this.status = 3; // 已结束
            }
        }
    }
}