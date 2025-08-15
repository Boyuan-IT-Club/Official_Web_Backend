package club.boyuan.official.dto;

public class SimpleResumeFieldDTO {
    private String fieldLabel;
    private String fieldValue;
    
    public SimpleResumeFieldDTO() {
    }
    
    public SimpleResumeFieldDTO(String fieldLabel, String fieldValue) {
        this.fieldLabel = fieldLabel;
        this.fieldValue = fieldValue;
    }
    
    // Getter 和 Setter 方法
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