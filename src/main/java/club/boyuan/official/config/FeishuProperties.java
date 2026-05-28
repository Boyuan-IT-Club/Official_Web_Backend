package club.boyuan.official.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书开放平台应用配置（请在飞书开发者后台创建企业自建应用并开通多维表格权限）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    /** 应用 App ID */
    private String appId = "";

    /** 应用 App Secret */
    private String appSecret = "";

    /** 飞书 Open API 域名 */
    private String apiBaseUrl = "https://open.feishu.cn";

    /** 单次 batch_create 最大条数（飞书上限 500） */
    private int batchSize = 100;
}
