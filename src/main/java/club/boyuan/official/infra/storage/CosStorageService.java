package club.boyuan.official.infra.storage;

import club.boyuan.official.infra.config.CosProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 基于腾讯云 COS 的文件存储服务，替代原先头像上传的本地磁盘存储。
 */
@Service
@RequiredArgsConstructor
public class CosStorageService {

    private final COSClient cosClient;
    private final CosProperties cosProperties;

    /**
     * 是否启用 COS（bucket 未配置时降级到本地磁盘）。
     */
    public boolean isEnabled() {
        return StringUtils.hasText(cosProperties.getBucket());
    }

    /**
     * 上传文件并返回对象键（objectKey），不包含对外访问域名。
     *
     * 默认只收图片：头像、活动图、二维码这些调用方都只该传图。
     */
    public String upload(MultipartFile file, String prefix) throws IOException {
        return upload(file, prefix, true);
    }

    /**
     * @param imageOnly 是否强制只收图片。
     *
     * 简历附件传 false —— 「任意格式」正是那个功能的需求，作品集可能是
     * PDF、压缩包、视频。放开类型不等于放开风险：附件的安全边界在**读取**那一侧
     * （ResumeAttachmentController：逐次鉴权、nosniff、只有安全类型才内联），
     * 而不是靠在这里拦住上传。其余调用方保持只收图片，别顺手一起放开。
     */
    public String upload(MultipartFile file, String prefix, boolean imageOnly) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        if (imageOnly && (file.getContentType() == null
                || !file.getContentType().startsWith("image/"))) {
            throw new IOException("不允许上传此类型的文件");
        }

        String key = buildObjectKey(prefix, file.getOriginalFilename());

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request =
                    new PutObjectRequest(cosProperties.getBucket(), key, inputStream, metadata);
            cosClient.putObject(request);
        }
        return key;
    }

    /**
     * 上传头像到配置的 avatarPrefix 目录，返回 objectKey。
     */
    public String uploadAvatar(MultipartFile file) throws IOException {
        return upload(file, cosProperties.getAvatarPrefix());
    }

    /**
     * 下载对象，返回原始输入流（调用方负责关闭）。
     */
    public InputStream download(String objectKey) {
        GetObjectRequest request = new GetObjectRequest(cosProperties.getBucket(), objectKey);
        COSObject cosObject = cosClient.getObject(request);
        return cosObject.getObjectContent();
    }

    /**
     * 打开对象，携带内容类型与长度，供流式返回使用。
     */
    public CosFile open(String objectKey) {
        GetObjectRequest request = new GetObjectRequest(cosProperties.getBucket(), objectKey);
        COSObject cosObject = cosClient.getObject(request);
        ObjectMetadata metadata = cosObject.getObjectMetadata();
        String contentType = metadata != null ? metadata.getContentType() : null;
        long contentLength = metadata != null ? metadata.getContentLength() : -1;
        return new CosFile(cosObject.getObjectContent(), contentType, contentLength);
    }

    /**
     * 删除对象。
     */
    public void delete(String objectKey) {
        cosClient.deleteObject(cosProperties.getBucket(), objectKey);
    }

    /**
     * 判断对象是否存在。
     */
    public boolean exists(String objectKey) {
        return cosClient.doesObjectExist(cosProperties.getBucket(), objectKey);
    }

    /**
     * 把数据库里的存储值转换为浏览器可访问的 URL。
     *
     * <p>兼容历史数据中已经保存的绝对 URL、站内根路径（例如 {@code /uploads/}
     * 与 {@code /api/files/}）以及 COS objectKey。站内路径必须原样返回，否则再次解析时会
     * 被错误拼成 {@code /api/files//api/files/...}。</p>
     */
    public String resolvePublicUrl(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return storedValue;
        }
        if (storedValue.startsWith("/")
                || storedValue.startsWith("http://")
                || storedValue.startsWith("https://")) {
            return storedValue;
        }

        String baseUrl = cosProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "/api/files/" + storedValue;
        }
        return baseUrl.replaceAll("/+$", "") + "/" + storedValue;
    }

    /**
     * 头像模块的兼容入口；新代码统一使用 {@link #resolvePublicUrl(String)}。
     */
    public String resolveAvatarUrl(String avatar) {
        return resolvePublicUrl(avatar);
    }

    private String buildObjectKey(String prefix, String originalFilename) {
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return normalizePrefix(prefix) + UUID.randomUUID() + extension;
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String normalized = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }
}
