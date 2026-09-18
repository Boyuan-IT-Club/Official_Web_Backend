package club.boyuan.official.domain.resume.dto;

public class SimpleResumeFieldDTO {
    private Integer fieldId;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String placeholder;
    private String fieldValue;
    /**
     * 该字段在本周期里的排列序号（resume_field_definition.sort_order）。
     * 带出来是为了让 PDF、Word、网页三处按同一个顺序展示 ——
     * 此前各有一套写死的顺序，同一份简历在三个地方长得都不一样。
     */
    private Integer sortOrder;
    /**
     * 该字段在本周期是否必填（resume_field_definition.is_required）。
     * 带出来是因为**空值必须以必填性区分对待**：必填留空是候选人没写，
     * 可选留空只是这项他没有，两者不该同等看待。
     */
    private Boolean isRequired;

    public SimpleResumeFieldDTO() {
    }
    
    public SimpleResumeFieldDTO(Integer fieldId, String fieldLabel, String fieldValue) {
        this.fieldId = fieldId;
        this.fieldLabel = fieldLabel;
        this.fieldValue = fieldValue;
    }

    public SimpleResumeFieldDTO(Integer fieldId, String fieldKey, String fieldLabel,
                                String fieldType, String placeholder, String fieldValue,
                                Integer sortOrder, Boolean isRequired) {
        this(fieldId, fieldKey, fieldLabel, fieldType, placeholder, fieldValue);
        this.sortOrder = sortOrder;
        this.isRequired = isRequired;
    }

    public SimpleResumeFieldDTO(Integer fieldId, String fieldKey, String fieldLabel,
                                String fieldType, String placeholder, String fieldValue) {
        this.fieldId = fieldId;
        this.fieldKey = fieldKey;
        this.fieldLabel = fieldLabel;
        this.fieldType = fieldType;
        this.placeholder = placeholder;
        this.fieldValue = fieldValue;
    }
    
    // Getter 和 Setter 方法
    public Integer getFieldId() {
        return fieldId;
    }
    
    public void setFieldId(Integer fieldId) {
        this.fieldId = fieldId;
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
    
    public String getFieldValue() {
        return fieldValue;
    }
    
    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }
}