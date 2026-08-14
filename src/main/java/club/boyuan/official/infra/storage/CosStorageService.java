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
     */
    public String upload(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
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
     * 把数据库里存储的头像值转换为对外可访问的 URL。
     *
     * <p>兼容三种存量格式：{@code /uploads/} 开头的本地老路径原样返回；
     * 已是完整 http(s) 地址的原样返回；其余视为 objectKey，用 public-base-url 拼接。
     */
    public String resolveAvatarUrl(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            return avatar;
        }
        if (avatar.startsWith("/uploads/")
                || avatar.startsWith("http://")
                || avatar.startsWith("https://")) {
            return avatar;
        }

        String baseUrl = cosProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "/api/files/" + avatar;
        }
        return baseUrl.replaceAll("/+$", "") + "/" + avatar;
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
