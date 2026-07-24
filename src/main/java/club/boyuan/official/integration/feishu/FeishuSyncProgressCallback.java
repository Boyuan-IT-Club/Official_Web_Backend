package club.boyuan.official.integration.feishu;

/**
 * 飞书同步任务进度回调（桶/行完成时触发，用于 Redis + SSE）。
 */
@FunctionalInterface
public interface FeishuSyncProgressCallback {

    void onProgress(int completedSteps, int totalSteps, int successCount, int failedCount, int skippedCount);
}
