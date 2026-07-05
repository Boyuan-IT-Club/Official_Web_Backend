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
    
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String placeholder;
    
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
        this.fieldKey = fieldValueDTO.getFieldKey();
        this.fieldLabel = fieldValueDTO.getFieldLabel();
        this.fieldType = fieldValueDTO.getFieldType();
        this.placeholder = fieldValueDTO.getPlaceholder();
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
    
    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }
    
    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }
}