package club.boyuan.official.domain.interview.service;

import club.boyuan.official.integration.feishu.dto.FeishuSyncTaskStatusDTO;
import club.boyuan.official.integration.feishu.dto.FeishuSyncTaskSubmitDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuRequestDTO;
import club.boyuan.official.integration.feishu.FeishuSyncTaskRecord;

/**
 * 飞书导入服务接口。
 *
 * <p>异步模型：HTTP 只负责 submit + poll；真正写飞书在 MQ 消费者线程里。
 */
public interface InterviewFeishuImportService {

    /** POST /import：写 Redis(PENDING) + 发 MQ，立即返回 taskId */
    FeishuSyncTaskSubmitDTO submitImportTask(ImportFeishuRequestDTO request);

    /** POST /import-from-table：飞书 → 平台异步拉回 */
    FeishuSyncTaskSubmitDTO submitPullFromTableTask(ImportFromFeishuTableRequestDTO request, Integer operatorUserId);

    /** GET /import/tasks/{id}：读 Redis，前端轮询直到终态 */
    FeishuSyncTaskStatusDTO getImportTaskStatus(Long taskId);

    /** FeishuSyncConsumer 调用：抢占任务 → 执行 PUSH 或 PULL → 回写 Redis */
    void runImportTask(Long taskId);

    FeishuSyncTaskStatusDTO toStatusDto(FeishuSyncTaskRecord record);
}
