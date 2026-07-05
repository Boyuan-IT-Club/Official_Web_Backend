package club.boyuan.official.dto;

import club.boyuan.official.entity.ResumeFieldDefinition;

import java.time.LocalDateTime;

public class ResumeFieldDefinitionDTO {
    private Integer fieldId;
    private Integer cycleId;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String placeholder;
    private Boolean isRequired;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 构造函数
    public ResumeFieldDefinitionDTO() {
    }
    
    // Getter 和 Setter 方法
    public Integer getFieldId() {
        return fieldId;
    }
    
    public void setFieldId(Integer fieldId) {
        this.fieldId = fieldId;
    }
    
    public Integer getCycleId() {
        return cycleId;
    }
    
    public void setCycleId(Integer cycleId) {
        this.cycleId = cycleId;
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
    
    public Boolean getIsRequired() {
        return isRequired;
    }
    
    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }
    
    public Integer getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
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

    public static ResumeFieldDefinitionDTO fromEntity(ResumeFieldDefinition entity) {
        if (entity == null) {
            return null;
        }
        ResumeFieldDefinitionDTO dto = new ResumeFieldDefinitionDTO();
        dto.setFieldId(entity.getFieldId());
        dto.setCycleId(entity.getCycleId());
        dto.setFieldKey(entity.getFieldKey());
        dto.setFieldLabel(entity.getFieldLabel());
        dto.setFieldType(entity.getFieldType());
        dto.setPlaceholder(entity.getPlaceholder());
        dto.setIsRequired(entity.getIsRequired());
        dto.setSortOrder(entity.getSortOrder());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}