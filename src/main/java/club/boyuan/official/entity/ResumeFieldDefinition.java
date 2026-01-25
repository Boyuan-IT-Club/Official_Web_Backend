package club.boyuan.official.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_field_definition")
@TableName("resume_field_definition") // MyBatis-Plus 表映射注解
public class ResumeFieldDefinition {
    @Id
    @TableId(value = "field_id", type = IdType.AUTO) // MyBatis-Plus 主键映射，指定自增策略
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "field_id")
    private Integer fieldId;

    @Column(name = "cycle_id", nullable = false)
    @TableField("cycle_id") // MyBatis-Plus 字段映射
    private Integer cycleId;

    @Column(name = "field_key", nullable = false)
    @TableField("field_key")
    private String fieldKey;

    @Column(name = "field_label", nullable = false)
    @TableField("field_label")
    private String fieldLabel;

    @Column(name = "is_required", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @TableField("is_required")
    private Boolean isRequired;

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0")
    @TableField("sort_order")
    private Integer sortOrder;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @TableField("is_active")
    private Boolean isActive;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT) // 自动填充创建时间
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
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
}