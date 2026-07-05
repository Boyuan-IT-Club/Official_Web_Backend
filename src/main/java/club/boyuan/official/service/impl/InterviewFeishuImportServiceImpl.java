package club.boyuan.official.service.impl;

import club.boyuan.official.dto.FeishuSyncTaskStatusDTO;
import club.boyuan.official.dto.FeishuSyncTaskSubmitDTO;
import club.boyuan.official.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.feishu.FeishuImportExecutor;
import club.boyuan.official.feishu.FeishuSyncProgressCallback;
import club.boyuan.official.feishu.FeishuSyncTaskType;
import club.boyuan.official.feishu.FeishuTablePullImportExecutor;
import club.boyuan.official.feishu.FeishuSyncTaskRecord;
import club.boyuan.official.feishu.FeishuSyncTaskRedisStore;
import club.boyuan.official.feishu.FeishuSyncTaskStatus;
import club.boyuan.official.messaging.ReliableMessagePublisher;
import club.boyuan.official.service.InterviewFeishuImportService;
import club.boyuan.official.sse.AsyncTaskChannel;
import club.boyuan.official.sse.AsyncTaskSseHub;
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
    private final ReliableMessagePublisher reliableMessagePublisher;
    private final FeishuImportExecutor feishuImportExecutor;
    private final FeishuTablePullImportExecutor feishuTablePullImportExecutor;
    private final AsyncTaskSseHub asyncTaskSseHub;

    /** 同步部分：写 Redis + 发 MQ；耗时导入在消费者里异步跑。 */
    @Override
    public FeishuSyncTaskSubmitDTO submitImportTask(ImportFeishuRequestDTO request) {
        FeishuSyncTaskRecord task = taskRedisStore.create(request);
        enqueueFeishuSyncMessage(task.getTaskId());
        log.info("飞书导入任务已创建(Redis) taskId={}, cycleId={}", task.getTaskId(), request.getCycleId());

        FeishuSyncTaskSubmitDTO submit = new FeishuSyncTaskSubmitDTO()
                .setTaskId(task.getTaskId())
                .setStatus(FeishuSyncTaskStatus.PENDING.name());
        publishFeishuSse(task.getTaskId(), toStatusDto(task), false);
        return submit;
    }

    @Override
    public FeishuSyncTaskSubmitDTO submitPullFromTableTask(
            ImportFromFeishuTableRequestDTO request, Integer operatorUserId) {
        FeishuSyncTaskRecord task = taskRedisStore.createPull(request, operatorUserId);
        enqueueFeishuSyncMessage(task.getTaskId());
        log.info("飞书拉回任务已创建 taskId={}, cycleId={}", task.getTaskId(), request.getCycleId());

        FeishuSyncTaskSubmitDTO submit = new FeishuSyncTaskSubmitDTO()
                .setTaskId(task.getTaskId())
                .setStatus(FeishuSyncTaskStatus.PENDING.name());
        publishFeishuSse(task.getTaskId(), toStatusDto(task), false);
        return submit;
    }

    private void enqueueFeishuSyncMessage(Long taskId) {
        reliableMessagePublisher.publishFeishuSync(String.valueOf(taskId));
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
        publishFeishuSse(taskId, toStatusDto(task), false);

        try {
            if (task.resolvedTaskType() == FeishuSyncTaskType.PULL_FROM_FEISHU) {
                runPullTask(taskId, task);
            } else {
                runPushTask(taskId, task);
            }
            publishFeishuSse(taskId, toStatusDto(taskRedisStore.requireById(taskId)), true);
        } catch (BusinessException ex) {
            log.error("飞书导入任务业务失败 taskId={}", taskId, ex);
            taskRedisStore.fail(taskId, ex.getMessage());
            publishFeishuSse(taskId, toStatusDto(taskRedisStore.requireById(taskId)), true);
            throw ex;
        } catch (Exception ex) {
            log.error("飞书导入任务异常 taskId={}", taskId, ex);
            taskRedisStore.fail(taskId, "飞书导入异常: " + ex.getMessage());
            publishFeishuSse(taskId, toStatusDto(taskRedisStore.requireById(taskId)), true);
            throw ex;
        }
    }

    private void runPushTask(Long taskId, FeishuSyncTaskRecord task) {
        ImportFeishuRequestDTO request = task.toImportRequest();
        ImportFeishuResponseDTO result = feishuImportExecutor.execute(request, progressReporter(taskId));
        FeishuSyncTaskRecord latest = taskRedisStore.requireById(taskId);
        if (latest.getTotalSteps() == null && result.getLocations() != null) {
            latest.setTotalSteps(result.getLocations().size());
        }
        taskRedisStore.complete(taskId, result, resolvePushFinalStatus(result));
    }

    private void runPullTask(Long taskId, FeishuSyncTaskRecord task) {
        ImportFromFeishuTableResponseDTO result = feishuTablePullImportExecutor.execute(
                task.toPullRequest(), task.getOperatorUserId(), progressReporter(taskId));
        taskRedisStore.completePull(taskId, result, resolvePullFinalStatus(result));
    }

    private FeishuSyncProgressCallback progressReporter(Long taskId) {
        return (completedSteps, totalSteps, successCount, failedCount, skippedCount) -> {
            taskRedisStore.updateProgress(taskId, completedSteps, totalSteps, successCount, failedCount, skippedCount);
            publishFeishuSse(taskId, toStatusDto(taskRedisStore.requireById(taskId)), false);
        };
    }

    private void publishFeishuSse(Long taskId, FeishuSyncTaskStatusDTO dto, boolean terminal) {
        asyncTaskSseHub.publish(AsyncTaskChannel.FEISHU, String.valueOf(taskId), dto, terminal);
    }

    @Override
    public FeishuSyncTaskStatusDTO toStatusDto(FeishuSyncTaskRecord task) {
        String taskType = task.getTaskType();
        if (taskType == null) {
            taskType = FeishuSyncTaskType.PUSH_TO_FEISHU.name();
        }
        return new FeishuSyncTaskStatusDTO()
                .setTaskId(task.getTaskId())
                .setTaskType(taskType)
                .setStatus(task.getStatus())
                .setImportedCount(task.getImportedCount())
                .setFailedCount(task.getFailedCount())
                .setSkippedCount(task.getSkippedCount())
                .setTotalSteps(task.getTotalSteps())
                .setCompletedSteps(task.getCompletedSteps())
                .setProgressPercent(task.getProgressPercent())
                .setErrorMessage(task.getErrorMessage())
                .setResult(task.getResult())
                .setPullResult(task.getPullResult())
                .setCreatedAt(task.getCreatedAt())
                .setStartedAt(task.getStartedAt())
                .setFinishedAt(task.getFinishedAt());
    }

    private FeishuSyncTaskStatus resolvePushFinalStatus(ImportFeishuResponseDTO result) {
        if (result.getImportedCount() > 0 && result.getFailedCount() == 0) {
            return FeishuSyncTaskStatus.SUCCESS;
        }
        if (result.getImportedCount() > 0) {
            return FeishuSyncTaskStatus.PARTIAL_SUCCESS;
        }
        return FeishuSyncTaskStatus.FAILED;
    }

    private FeishuSyncTaskStatus resolvePullFinalStatus(ImportFromFeishuTableResponseDTO result) {
        if (result.getSuccessCount() > 0 && result.getFailedCount() == 0) {
            return FeishuSyncTaskStatus.SUCCESS;
        }
        if (result.getSuccessCount() > 0) {
            return FeishuSyncTaskStatus.PARTIAL_SUCCESS;
        }
        return FeishuSyncTaskStatus.FAILED;
    }
}
