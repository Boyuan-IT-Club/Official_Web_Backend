package club.boyuan.official.domain.resume.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ResumeDTO {
    private Integer resumeId;
    private Integer userId;
    private Integer cycleId;
    private Integer status;
    /** 简历评分（0~100，管理员在简历审核里打） */
    private Integer resumeScore;
    /** 候选人注册姓名（user.name），简历字段缺姓名时管理端用它兜底展示 */
    private String userName;
    /** 候选人注册邮箱（user.email） */
    private String userEmail;
    /** 打分人 userId（署名），历史分数为 null */
    private Integer scoredBy;
    /** 打分人姓名，管理端展示用 */
    private String scoredByName;
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Integer getScoredBy() {
        return scoredBy;
    }

    public void setScoredBy(Integer scoredBy) {
        this.scoredBy = scoredBy;
    }

    public String getScoredByName() {
        return scoredByName;
    }

    public void setScoredByName(String scoredByName) {
        this.scoredByName = scoredByName;
    }

    public Integer getResumeScore() {
        return resumeScore;
    }

    public void setResumeScore(Integer resumeScore) {
        this.resumeScore = resumeScore;
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