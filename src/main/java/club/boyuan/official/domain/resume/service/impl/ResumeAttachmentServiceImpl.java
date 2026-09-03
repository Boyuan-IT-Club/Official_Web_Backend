package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.dto.ResumeAttachmentDTO;
import club.boyuan.official.domain.resume.service.IResumeAttachmentService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeAttachment;
import club.boyuan.official.persistence.mapper.ResumeAttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAttachmentServiceImpl implements IResumeAttachmentService {

    /** 单个附件上限。作品集、成绩单这类 20MB 足够，再大该给网盘链接 */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    /** 每份简历的附件数量上限，避免有人把这里当网盘 */
    private static final int MAX_COUNT = 10;

    /**
     * 允许内联预览的 MIME。
     *
     * 这份清单是**安全边界**，不是便利设置。附件由申请人上传、内容不可信，
     * 而接口与站点同源：若把 text/html 或 image/svg+xml 内联返回，
     * 上传者就能在管理端的源上执行任意脚本，读走面试官的登录态——存储型 XSS。
     * 所以只放行「浏览器渲染但不执行脚本」的类型，其余一律强制下载。
     *
     * 注意 svg 不在其中：它能带 <script>，是常被忽略的一个口子。
     */
    private static final Set<String> INLINE_SAFE_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp",
            "text/plain",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/wav", "audio/ogg"
    );

    private final ResumeAttachmentMapper attachmentMapper;
    private final CosStorageService cosStorageService;
    private final IResumeService resumeService;

    @Override
    public ResumeAttachmentDTO upload(Integer resumeId, Integer currentUserId, MultipartFile file) {
        Resume resume = requireOwnResume(resumeId, currentUserId);

        // 与保存字段值同一道闸：周期关闭后不再接受任何学生侧写入
        resumeService.assertCycleOpen(resume.getCycleId());

        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "单个附件不要超过 20MB");
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());

        List<ResumeAttachment> existing = rawListByResume(resumeId);
        if (existing.size() >= MAX_COUNT) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "附件最多 " + MAX_COUNT + " 个，请先删掉不需要的");
        }
        // 同名视为「替换」：重名会撞唯一索引，报数据库错误对用户毫无意义
        ResumeAttachment duplicate = existing.stream()
                .filter(a -> fileName.equals(a.getFileName()))
                .findFirst().orElse(null);

        String objectKey;
        try {
            objectKey = cosStorageService.upload(file, "attachments");
        } catch (IOException e) {
            log.error("附件上传失败 resumeId={}, file={}", resumeId, fileName, e);
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_FAILED);
        }

        if (duplicate != null) {
            duplicate.setObjectKey(objectKey)
                    .setContentType(file.getContentType())
                    .setSizeBytes(file.getSize());
            attachmentMapper.updateById(duplicate);
            log.info("附件已替换 resumeId={}, file={}", resumeId, fileName);
            return toDto(duplicate);
        }

        ResumeAttachment created = new ResumeAttachment()
                .setResumeId(resumeId)
                .setUserId(currentUserId)
                .setCycleId(resume.getCycleId())
                .setFileName(fileName)
                .setObjectKey(objectKey)
                .setContentType(file.getContentType())
                .setSizeBytes(file.getSize());
        attachmentMapper.insert(created);
        log.info("附件已上传 resumeId={}, file={}, size={}", resumeId, fileName, file.getSize());
        return toDto(created);
    }

    @Override
    public List<ResumeAttachmentDTO> listByResume(Integer resumeId) {
        return rawListByResume(resumeId).stream().map(this::toDto).toList();
    }

    @Override
    public void delete(Integer id, Integer currentUserId) {
        ResumeAttachment a = getOrThrow(id);
        if (!a.getUserId().equals(currentUserId)) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        resumeService.assertCycleOpen(a.getCycleId());
        attachmentMapper.deleteById(id);
        log.info("附件已删除 id={}, resumeId={}", id, a.getResumeId());
        // COS 上的对象刻意不删：同名替换后旧键仍可能被别处引用，
        // 且误删无法恢复。存储成本远低于丢失申请材料的代价。
    }

    @Override
    public ResumeAttachment getOrThrow(Integer id) {
        if (id == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        ResumeAttachment a = attachmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_ATTACHMENT_NOT_FOUND);
        }
        return a;
    }

    @Override
    public CosFile open(ResumeAttachment attachment) {
        return cosStorageService.open(attachment.getObjectKey());
    }

    @Override
    public boolean previewable(String contentType, String fileName) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        // 去掉 charset 之类的参数再比对
        String base = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return INLINE_SAFE_TYPES.contains(base);
    }

    private List<ResumeAttachment> rawListByResume(Integer resumeId) {
        if (resumeId == null) {
            return List.of();
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<ResumeAttachment>()
                .eq(ResumeAttachment::getResumeId, resumeId)
                .orderByAsc(ResumeAttachment::getId));
    }

    private ResumeAttachmentDTO toDto(ResumeAttachment e) {
        return ResumeAttachmentDTO.of(e, previewable(e.getContentType(), e.getFileName()));
    }

    /** 只能往自己的简历上传/删附件。 */
    private Resume requireOwnResume(Integer resumeId, Integer currentUserId) {
        if (resumeId == null || currentUserId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (!currentUserId.equals(resume.getUserId())) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        return resume;
    }

    /**
     * 文件名消毒。
     *
     * 原始文件名会被写进 Content-Disposition，也会显示在管理端。
     * 路径分隔符能拼出「../」，换行与引号能截断/污染响应头，
     * 都必须在入库前去掉——而不是等取用时再想起来。
     */
    private static String sanitizeFileName(String raw) {
        String name = StringUtils.hasText(raw) ? raw : "附件";
        // 只取最后一段，挡掉 ../ 与 Windows 盘符路径
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"]", "").trim();
        if (name.isEmpty()) {
            name = "附件";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
