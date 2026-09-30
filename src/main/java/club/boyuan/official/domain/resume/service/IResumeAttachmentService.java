package club.boyuan.official.domain.resume.service;

import club.boyuan.official.domain.resume.dto.ResumeAttachmentDTO;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.persistence.entity.ResumeAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 简历附件：学生上传任意格式资料，面试官在管理端预览或下载。 */
public interface IResumeAttachmentService {

    /** 上传。周期须开放投递，且只能传到自己的简历上。 */
    ResumeAttachmentDTO upload(Integer resumeId, Integer currentUserId, MultipartFile file);

    List<ResumeAttachmentDTO> listByResume(Integer resumeId);

    /** 删除。只有上传者本人可删，且周期仍开放。 */
    void delete(Integer id, Integer currentUserId);

    /** 取元信息（鉴权与下载头都要用到）。 */
    ResumeAttachment getOrThrow(Integer id);

    /** 打开文件流。 */
    CosFile open(ResumeAttachment attachment);

    /** 该类型能否安全地内联预览。 */
    boolean previewable(String contentType, String fileName);

    /**
     * 签发附件的限时直链（浏览器直接从 COS 下载）。
     * 只有服务端认定安全的类型才以 inline 下发，其余一律 attachment。
     *
     * @return 链接；COS 未启用时返回 null，调用方退回 /content 流式接口
     */
    String presignedUrl(ResumeAttachment attachment, boolean inline);
}
