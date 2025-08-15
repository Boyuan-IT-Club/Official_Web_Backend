package club.boyuan.official.dto;

import club.boyuan.official.entity.ResumeFieldDefinition;
import java.time.LocalDateTime;

public class ResumeFieldValueDTO {
    private Integer valueId;
    private Integer resumeId;
    private Integer fieldId;
    private String fieldValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 简化字段信息（仅包含字段标签）
    private String fieldLabel;
    
    // 构造函数
    public ResumeFieldValueDTO() {
    }
    
    public ResumeFieldValueDTO(ResumeFieldValueDTO fieldValueDTO) {
        this.valueId = fieldValueDTO.getValueId();
        this.resumeId = fieldValueDTO.getResumeId();
        this.fieldId = fieldValueDTO.getFieldId();
        this.fieldValue = fieldValueDTO.getFieldValue();
        this.createdAt = fieldValueDTO.getCreatedAt();
        this.updatedAt = fieldValueDTO.getUpdatedAt();
        this.fieldLabel = fieldValueDTO.getFieldLabel();
    }
    
    // Getter 和 Setter 方法
    public Integer getValueId() {
        return valueId;
    }
    
    public void setValueId(Integer valueId) {
        this.valueId = valueId;
    }
    
    public Integer getResumeId() {
        return resumeId;
    }
    
    public void setResumeId(Integer resumeId) {
        this.resumeId = resumeId;
    }
    
    public Integer getFieldId() {
        return fieldId;
    }
    
    public void setFieldId(Integer fieldId) {
        this.fieldId = fieldId;
    }
    
    public String getFieldValue() {
        return fieldValue;
    }
    
    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
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
    
    public String getFieldLabel() {
        return fieldLabel;
    }
    
    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }
}