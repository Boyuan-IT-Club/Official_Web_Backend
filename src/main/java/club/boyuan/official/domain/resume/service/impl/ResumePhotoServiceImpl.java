package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IResumePhotoService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumePhotoServiceImpl implements IResumePhotoService {

    /**
     * 照片专用前缀。读取侧只认这个前缀的 key：字段值是学生可写的普通字符串，
     * 若不限前缀，就等于把「任意 COS 对象」的读取能力交给了照片接口。
     */
    private static final String PHOTO_PREFIX = "resume-photos/";

    /** 与前端 PhotoUpload 的校验一致；前端还会先压到 ≤800px jpeg，正常远小于此 */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** 照片只收真正的位图。svg 能带脚本，与附件内联白名单同一道理，不收 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

    private static final String PHOTO_FIELD_KEY = "personal_photo";

    private final CosStorageService cosStorageService;
    private final IResumeService resumeService;
    private final ResumeFieldDefinitionMapper fieldDefinitionMapper;

    @Override
    public String upload(Integer resumeId, Integer currentUserId, MultipartFile file) {
        Resume resume = requireOwnResume(resumeId, currentUserId);
        // 与字段保存、附件上传同一道闸：周期关闭后不再接受学生侧写入
        resumeService.assertCycleOpen(resume.getCycleId());

        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请选择要上传的照片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "照片不要超过 5MB");
        }
        String contentType = normalizeType(file.getContentType());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED,
                    "只支持 png / jpeg / gif / webp / bmp 格式的照片");
        }

        try {
            String objectKey = cosStorageService.upload(file, PHOTO_PREFIX);
            log.info("简历照片已上传 resumeId={}, key={}, size={}", resumeId, objectKey, file.getSize());
            return objectKey;
        } catch (IOException e) {
            log.error("简历照片上传失败 resumeId={}", resumeId, e);
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public CosFile openByResumeId(Integer resumeId) {
        String value = findPhotoFieldValue(resumeId);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND);
        }
        if (value.startsWith("data:")) {
            return decodeDataUrl(value);
        }
        if (!value.startsWith(PHOTO_PREFIX)) {
            // 字段值被写成了别的东西（脏数据、或有人试图借道读任意 key），一律当没有照片
            log.warn("简历照片字段值不是合法引用 resumeId={}", resumeId);
            throw new BusinessException(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND);
        }
        return cosStorageService.open(value);
    }

    @Override
    public byte[] readBytesIfObjectKey(String fieldValue) {
        if (!StringUtils.hasText(fieldValue) || !fieldValue.startsWith(PHOTO_PREFIX)) {
            return null;
        }
        try (InputStream in = cosStorageService.download(fieldValue)) {
            return in.readAllBytes();
        } catch (Exception e) {
            // 导出别因为照片取不到而整体失败，降级为无照片
            log.warn("PDF 导出取照片失败 key={}", fieldValue, e);
            return null;
        }
    }

    /** 找到该简历 personal_photo 字段的值；简历不存在抛 404，没填返回 null */
    private String findPhotoFieldValue(Integer resumeId) {
        if (resumeId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        List<ResumeFieldDefinition> defs = fieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, resume.getCycleId())
                        .eq(ResumeFieldDefinition::getFieldKey, PHOTO_FIELD_KEY));
        if (defs.isEmpty()) {
            return null;
        }
        Set<Integer> photoFieldIds = defs.stream().map(ResumeFieldDefinition::getFieldId)
                .collect(java.util.stream.Collectors.toSet());
        return resumeService.getFieldValuesByResumeId(resumeId).stream()
                .filter(v -> v.getFieldId() != null && photoFieldIds.contains(v.getFieldId()))
                .map(ResumeFieldValue::getFieldValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /** 历史数据：整段 data:image/...;base64,... 就地解码 */
    private CosFile decodeDataUrl(String value) {
        int comma = value.indexOf(',');
        int colon = value.indexOf(':');
        int semi = value.indexOf(';');
        if (comma < 0 || semi < 0 || semi > comma) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND);
        }
        String contentType = value.substring(colon + 1, semi);
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
            return new CosFile(new ByteArrayInputStream(bytes), contentType, bytes.length);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_PHOTO_NOT_FOUND);
        }
    }

    private String normalizeType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    /** 只能给自己的简历传照片（与附件上传同规则）。 */
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
}
