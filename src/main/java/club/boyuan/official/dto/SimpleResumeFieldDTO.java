package club.boyuan.official.dto;

public class SimpleResumeFieldDTO {
    private Integer fieldId;
    private String fieldLabel;
    private String fieldValue;
    
    public SimpleResumeFieldDTO() {
    }
    
    public SimpleResumeFieldDTO(Integer fieldId, String fieldLabel, String fieldValue) {
        this.fieldId = fieldId;
        this.fieldLabel = fieldLabel;
        this.fieldValue = fieldValue;
    }
    
    // Getter 和 Setter 方法
    public Integer getFieldId() {
        return fieldId;
    }
    
    public void setFieldId(Integer fieldId) {
        this.fieldId = fieldId;
    }
    
    public String getFieldLabel() {
        return fieldLabel;
    }
    
    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }
    
    public String getFieldValue() {
        return fieldValue;
    }
    
    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}