package club.boyuan.official.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FeedbackCreateRequest {

    /** bug / suggestion / other；不传按 other */
    @jakarta.validation.constraints.Pattern(regexp = "^(bug|suggestion|other)$", message = "反馈分类不合法")
    private String category;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 5000, message = "反馈内容不能超过5000个字符")
    private String content;

    /**
     * 截图的 objectKey，先调上传接口拿到再随反馈一起提交。
     * 限 3 张：再多管理端也看不过来，而且每张都要走一次鉴权取图。
     */
    @Size(max = 3, message = "最多上传 3 张截图")
    private java.util.List<String> imageKeys;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public java.util.List<String> getImageKeys() { return imageKeys; }
    public void setImageKeys(java.util.List<String> imageKeys) { this.imageKeys = imageKeys; }
}
