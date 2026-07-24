package club.boyuan.official.domain.resume.dto;

public class SimpleResumeFieldDTO {
    private Integer fieldId;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String placeholder;
    private String fieldValue;
    
    public SimpleResumeFieldDTO() {
    }
    
    public SimpleResumeFieldDTO(Integer fieldId, String fieldLabel, String fieldValue) {
        this.fieldId = fieldId;
        this.fieldLabel = fieldLabel;
        this.fieldValue = fieldValue;
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
}