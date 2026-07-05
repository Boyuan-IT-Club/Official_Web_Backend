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

    /** 单次 list records 分页大小（飞书上限 500） */
    private int listPageSize = 500;

    /** 并行写入飞书的多地点（桶）并发数，注意飞书 API 频率限制 */
    private int parallelBucketConcurrency = 3;

    /** 飞书 API 失败后的最大重试次数（不含首次） */
    private int apiMaxRetries = 3;

    /** 指数退避初始间隔（毫秒） */
    private long apiInitialBackoffMs = 500;

    /** 指数退避上限（毫秒） */
    private long apiMaxBackoffMs = 8000;

    /** 连续失败多少次后打开熔断 */
    private int circuitFailureThreshold = 5;

    /** 熔断保持时间（秒） */
    private int circuitOpenSeconds = 30;

    /** 任务状态在 Redis 中的保留天数 */
    private int taskTtlDays = 7;

    /** 消费端抢占锁 TTL（小时），防止进程崩溃后永久无法重试 */
    private int taskLockTtlHours = 24;
}
