package club.boyuan.official.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** 用户问题反馈。反馈本身只支持提交和查看，不包含处理状态或回复。 */
@TableName(value = "feedback", autoResultMap = true)
public class Feedback {

    @TableId(value = "feedback_id", type = IdType.AUTO)
    private Long feedbackId;

    @TableField("user_id")
    private Integer userId;

    /** 分类：bug / suggestion / other。管理端据此归类与筛选 */
    @TableField("category")
    private String category;

    @TableField("content")
    private String content;

    /**
     * 截图的 COS objectKey 列表（最多 3 张）。
     *
     * 只存引用不存图：与简历照片/附件同一套存储。注意 @TableName 上必须带
     * autoResultMap = true，否则 BaseMapper 的查询不会走 typeHandler，
     * 读出来是原始 JSON 字符串（ResumeFieldDefinition.options 踩过同样的坑）。
     */
    @TableField(value = "image_keys", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<String> imageKeys;

    /** 0 未处理 / 1 已处理。只标不回：回复提交人是另一回事 */
    @TableField("handled")
    private Integer handled;

    @TableField("handled_by")
    private Integer handledBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("handled_at")
    private LocalDateTime handledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;

    public Long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public java.util.List<String> getImageKeys() { return imageKeys; }
    public void setImageKeys(java.util.List<String> imageKeys) { this.imageKeys = imageKeys; }
    public Integer getHandled() { return handled; }
    public void setHandled(Integer handled) { this.handled = handled; }
    public Integer getHandledBy() { return handledBy; }
    public void setHandledBy(Integer handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
