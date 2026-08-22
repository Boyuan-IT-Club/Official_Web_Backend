package club.boyuan.official.persistence.entity;

import java.util.List;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@TableName(value = "resume_field_definition", autoResultMap = true) // MyBatis-Plus 表映射注解
public class ResumeFieldDefinition {
    @TableId(value = "field_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    private Integer fieldId;

    @TableField("cycle_id") // MyBatis-Plus 字段映射
    private Integer cycleId;

    @TableField("field_key")
    private String fieldKey;

    @TableField("field_label")
    private String fieldLabel;

    @TableField("field_type")
    private String fieldType;

    @TableField("placeholder")
    private String placeholder;

    @TableField("is_required")
    private Boolean isRequired;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_active")
    private Boolean isActive;

    /**
     * 下拉/单选/多选的选项列表，库里是 JSON 字符串数组（如 ["男","女"]）。
     *
     * 这一列是后补的（V25）。此前管理端能编辑选项、前端类型也带着它，
     * 但库里无处存放 —— 保存时被静默丢掉，投递页的下拉只能靠前端写死的常量。
     *
     * 用 JacksonTypeHandler 而非映射成 String：它是有类型的字符串数组，
     * 让 Jackson 与 MyBatis 各自按 List<String> 处理，避免两端手工序列化。
     * 注意 @TableName 上必须带 autoResultMap = true，否则 BaseMapper 的查询
     * 不会应用这个 TypeHandler；自定义 XML 的 resultMap 里也要显式声明。
     */
    @TableField(value = "options", typeHandler = JacksonTypeHandler.class)
    private List<String> options;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) // 自动填充更新时间
    private LocalDateTime updatedAt;

    // 构造函数
    public ResumeFieldDefinition() {
    }

    public ResumeFieldDefinition(Integer cycleId, String fieldKey, String fieldLabel) {
        this.cycleId = cycleId;
        this.fieldKey = fieldKey;
        this.fieldLabel = fieldLabel;
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

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
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
}