package club.boyuan.official.domain.resume.dto;

import club.boyuan.official.persistence.entity.ResumeAttachment;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 对外的附件信息。
 *
 * 刻意不含 objectKey：那是存储实现细节，泄出去等于告诉外面桶里的路径。
 * 取文件一律走 /api/resumes/attachments/{id}/content 这条带鉴权的通道。
 */
@Data
@Accessors(chain = true)
public class ResumeAttachmentDTO {

    private Integer id;
    private Integer resumeId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createdAt;

    /** 浏览器能否直接内联预览；false 时前端只给「下载」 */
    private boolean previewable;

    public static ResumeAttachmentDTO of(ResumeAttachment e, boolean previewable) {
        return new ResumeAttachmentDTO()
                .setId(e.getId())
                .setResumeId(e.getResumeId())
                .setFileName(e.getFileName())
                .setContentType(e.getContentType())
                .setSizeBytes(e.getSizeBytes())
                .setCreatedAt(e.getCreatedAt())
                .setPreviewable(previewable);
    }
}
