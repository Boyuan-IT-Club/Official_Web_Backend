package club.boyuan.official.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FeedbackCreateRequest {

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 5000, message = "反馈内容不能超过5000个字符")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
