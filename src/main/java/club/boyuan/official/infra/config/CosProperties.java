package club.boyuan.official.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS 对象存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cos")
public class CosProperties {

    /** SecretId */
    private String secretId = "";

    /** SecretKey */
    private String secretKey = "";

    /** 地域，如 ap-shanghai */
    private String region = "ap-shanghai";

    /** 存储桶名称（含 APPID 后缀），如 example-1250000000 */
    private String bucket = "";

    /** 对外访问基地址，如 https://static.boyuan.club；为空时回退到 /api/files 后端中转 */
    private String publicBaseUrl = "";

    /** 头像文件在存储桶中的目录前缀 */
    private String avatarPrefix = "avatars/";
}
