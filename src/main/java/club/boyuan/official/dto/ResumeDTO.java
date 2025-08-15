package club.boyuan.official.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ResumeDTO {
    private Integer resumeId;
    private Integer userId;
    private Integer cycleId;
    private Integer status;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 关联的简化字段信息列表（仅包含字段标签和字段值）
    private List<SimpleResumeFieldDTO> simpleFields;
    
    // 构造函数
    public ResumeDTO() {
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
    
    public List<SimpleResumeFieldDTO> getSimpleFields() {
        return simpleFields;
    }
    
    public void setSimpleFields(List<SimpleResumeFieldDTO> simpleFields) {
        this.simpleFields = simpleFields;
    }
}