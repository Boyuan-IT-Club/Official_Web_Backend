package club.boyuan.official.domain.resume.service;

import club.boyuan.official.infra.storage.CosFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历个人照片（personal_photo 字段）。
 *
 * 照片字节存 COS（前缀 resume-photos/），resume_field_value 里只存 objectKey；
 * 历史数据中该字段是整段 base64 data URL，读取侧对两种形态都兼容。
 */
public interface IResumePhotoService {

    /**
     * 上传照片到 COS，返回 objectKey。
     *
     * 只传不落库：key 由前端塞进 personal_photo 字段值，随既有的字段保存流提交，
     * 保持「保存草稿 / 取消修改」语义不变。被替换的旧对象刻意不删（与附件同策略）。
     */
    String upload(Integer resumeId, Integer currentUserId, MultipartFile file);

    /**
     * 按简历取照片内容。字段值是 objectKey 时从 COS 拉流；
     * 是历史 base64 时就地解码——对调用方（前端）只有这一个读取入口。
     */
    CosFile openByResumeId(Integer resumeId);

    /**
     * 供 PDF 导出用：字段值是 objectKey 时取回字节；
     * 是历史 base64 或取不到时返回 null（导出侧自行降级为无照片）。
     */
    byte[] readBytesIfObjectKey(String fieldValue);
}
