package club.boyuan.official.service.impl;

import club.boyuan.official.dto.FeishuSyncTaskStatusDTO;
import club.boyuan.official.dto.FeishuSyncTaskSubmitDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.feishu.FeishuImportExecutor;
import club.boyuan.official.feishu.FeishuSyncTaskRecord;
import club.boyuan.official.feishu.FeishuSyncTaskRedisStore;
import club.boyuan.official.feishu.FeishuSyncTaskStatus;
import club.boyuan.official.messaging.FeishuSyncProducer;
import club.boyuan.official.service.InterviewFeishuImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 飞书导入「编排层」：只负责 Redis 任务状态 + MQ 投递 + 调用执行器，不直接请求飞书 API。
 *
 * <p>读代码时建议按这条链路看：
 * <ol>
 *   <li>{@link #submitImportTask} — HTTP 入口：创建任务、发 MQ、立刻返回 taskId</li>
 *   <li>{@link club.boyuan.official.messaging.FeishuSyncConsumer} — MQ 消费：调用 {@link #runImportTask}</li>
 *   <li>{@link club.boyuan.official.feishu.FeishuImportExecutor} — 真正查库、分组、写飞书</li>
 *   <li>{@link #getImportTaskStatus} — 前端轮询：只读 Redis，不看 MQ</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewFeishuImportServiceImpl implements InterviewFeishuImportService {

    private final FeishuSyncTaskRedisStore taskRedisStore;
    private final FeishuSyncProducer feishuSyncProducer;
    private final FeishuImportExecutor feishuImportExecutor;

    /** 同步部分：写 Redis + 发 MQ；耗时导入在消费者里异步跑。 */
    @Override
    public FeishuSyncTaskSubmitDTO submitImportTask(ImportFeishuRequestDTO request) {
        FeishuSyncTaskRecord task = taskRedisStore.create(request);
        feishuSyncProducer.publish(task.getTaskId());
        log.info("飞书导入任务已创建(Redis) taskId={}, cycleId={}", task.getTaskId(), request.getCycleId());

        return new FeishuSyncTaskSubmitDTO()
                .setTaskId(task.getTaskId())
                .setStatus(FeishuSyncTaskStatus.PENDING.name());
    }

    @Override
    public FeishuSyncTaskStatusDTO getImportTaskStatus(Long taskId) {
        FeishuSyncTaskRecord task = taskRedisStore.requireById(taskId);
        return toStatusDto(task);
    }

    /**
     * MQ 消费者入口：抢占任务后执行导入，并把结果写回 Redis。
     * <p>tryClaim 防止 MQ 重投或重复消费时同 taskId 跑两遍。
     */
    @Override
    public void runImportTask(Long taskId) {
        if (!taskRedisStore.tryClaim(taskId)) {
            log.info("飞书同步任务已处理或正在执行，跳过 taskId={}", taskId);
            return;
        }

        FeishuSyncTaskRecord task = taskRedisStore.requireById(taskId);
        // 提交时存的 cycleId/slotId 等参数，从 Redis 还原成执行器入参
        ImportFeishuRequestDTO request = task.toImportRequest();

        try {
            ImportFeishuResponseDTO result = feishuImportExecutor.execute(request);
            taskRedisStore.complete(taskId, result, resolveFinalStatus(result));
        } catch (BusinessException ex) {
            log.error("飞书导入任务业务失败 taskId={}", taskId, ex);
            taskRedisStore.fail(taskId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("飞书导入任务异常 taskId={}", taskId, ex);
            taskRedisStore.fail(taskId, "飞书导入异常: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public FeishuSyncTaskStatusDTO toStatusDto(FeishuSyncTaskRecord task) {
        return new FeishuSyncTaskStatusDTO()
                .setTaskId(task.getTaskId())
                .setStatus(task.getStatus())
                .setImportedCount(task.getImportedCount())
                .setFailedCount(task.getFailedCount())
                .setSkippedCount(task.getSkippedCount())
                .setErrorMessage(task.getErrorMessage())
                .setResult(task.getResult())
                .setCreatedAt(task.getCreatedAt())
                .setStartedAt(task.getStartedAt())
                .setFinishedAt(task.getFinishedAt());
    }

    /** 根据执行器汇总的 imported/failed 条数，映射任务终态（供前端轮询展示）。 */
    private FeishuSyncTaskStatus resolveFinalStatus(ImportFeishuResponseDTO result) {
        if (result.getImportedCount() > 0 && result.getFailedCount() == 0) {
            return FeishuSyncTaskStatus.SUCCESS;
        }
        if (result.getImportedCount() > 0) {
            return FeishuSyncTaskStatus.PARTIAL_SUCCESS;
        }
        return FeishuSyncTaskStatus.FAILED;
    }
}
