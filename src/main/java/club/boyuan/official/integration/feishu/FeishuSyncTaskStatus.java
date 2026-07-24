package club.boyuan.official.integration.feishu;

/**
 * 飞书导入异步任务状态（存 Redis，前端轮询展示）。
 */
public enum FeishuSyncTaskStatus {

    /** 已创建，等待 MQ 消费 */
    PENDING,
    /** 消费者已抢占，正在执行 FeishuImportExecutor */
    RUNNING,
    /** 全部导入成功（failedCount=0） */
    SUCCESS,
    /** 部分成功（有 imported 也有 failed） */
    PARTIAL_SUCCESS,
    /** 全部失败或执行抛错 */
    FAILED
}
