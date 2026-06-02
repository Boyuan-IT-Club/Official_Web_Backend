package club.boyuan.official.feishu;

import club.boyuan.official.config.FeishuProperties;
import club.boyuan.official.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 飞书同步任务状态存储（Redis），供「提交任务」与「前端轮询」使用；MQ 里只传 taskId。
 *
 * <p>Key 约定：
 * <ul>
 *   <li>{@value #SEQ_KEY} — INCR 生成自增 taskId</li>
 *   <li>{@code official:feishu:sync:task:{id}} — 任务 JSON（状态、计数、结果）</li>
 *   <li>{@code official:feishu:sync:lock:{id}} — 消费端抢占锁，防重复执行</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuSyncTaskRedisStore {

    /** 全局自增，生成 taskId */
    private static final String SEQ_KEY = "official:feishu:sync:task:seq";
    /** 任务详情 JSON */
    private static final String TASK_KEY_PREFIX = "official:feishu:sync:task:";
    /** 消费抢占锁（SETNX），与任务状态分离 */
    private static final String LOCK_KEY_PREFIX = "official:feishu:sync:lock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final FeishuProperties feishuProperties;

    /** 创建任务：状态 PENDING，并持久化导入参数（cycleId、slotId 等）供消费者还原。 */
    public FeishuSyncTaskRecord create(ImportFeishuRequestDTO request) {
        Long taskId = stringRedisTemplate.opsForValue().increment(SEQ_KEY);
        if (taskId == null) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "生成任务 ID 失败");
        }

        LocalDateTime now = LocalDateTime.now();
        FeishuSyncTaskRecord record = new FeishuSyncTaskRecord()
                .setTaskId(taskId)
                .setTaskType(FeishuSyncTaskType.PUSH_TO_FEISHU.name())
                .setCycleId(request.getCycleId())
                .setSlotId(request.getSlotId())
                .setFeishuTableUrl(StringUtils.hasText(request.getFeishuTableUrl())
                        ? request.getFeishuTableUrl().trim() : null)
                .setForceUpdate(Boolean.TRUE.equals(request.getForceUpdate()))
                .setStatus(FeishuSyncTaskStatus.PENDING.name())
                .setImportedCount(0)
                .setFailedCount(0)
                .setSkippedCount(0)
                .setCreatedAt(now);

        save(record);
        return record;
    }

    /** 创建飞书 → 平台拉回任务。 */
    public FeishuSyncTaskRecord createPull(ImportFromFeishuTableRequestDTO request, Integer operatorUserId) {
        Long taskId = stringRedisTemplate.opsForValue().increment(SEQ_KEY);
        if (taskId == null) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "生成任务 ID 失败");
        }

        LocalDateTime now = LocalDateTime.now();
        FeishuSyncTaskRecord record = new FeishuSyncTaskRecord()
                .setTaskId(taskId)
                .setTaskType(FeishuSyncTaskType.PULL_FROM_FEISHU.name())
                .setCycleId(request.getCycleId())
                .setFeishuTableUrl(request.getFeishuTableUrl().trim())
                .setUpdateUserDept(request.getUpdateUserDept())
                .setOperatorUserId(operatorUserId)
                .setStatus(FeishuSyncTaskStatus.PENDING.name())
                .setImportedCount(0)
                .setFailedCount(0)
                .setSkippedCount(0)
                .setCreatedAt(now);

        save(record);
        return record;
    }

    public Optional<FeishuSyncTaskRecord> findById(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        String json = stringRedisTemplate.opsForValue().get(taskKey(taskId));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, FeishuSyncTaskRecord.class));
        } catch (JsonProcessingException ex) {
            log.warn("解析飞书任务 Redis 数据失败 taskId={}", taskId, ex);
            return Optional.empty();
        }
    }

    public FeishuSyncTaskRecord requireById(Long taskId) {
        return findById(taskId)
                .orElseThrow(() -> new BusinessException(BusinessExceptionEnum.FEISHU_SYNC_TASK_NOT_FOUND));
    }

    /**
     * 消费端抢占任务（幂等入口）。
     * <ol>
     *   <li>SETNX 分布式锁 — 同一 taskId 同时只有一个消费者在跑</li>
     *   <li>仅 PENDING → RUNNING — MQ 重投时若已完成则直接 false</li>
     * </ol>
     */
    public boolean tryClaim(Long taskId) {
        String lockKey = lockKey(taskId);
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofHours(feishuProperties.getTaskLockTtlHours()));
        if (!Boolean.TRUE.equals(locked)) {
            return false;
        }

        FeishuSyncTaskRecord record = findById(taskId).orElse(null);
        if (record == null || !FeishuSyncTaskStatus.PENDING.name().equals(record.getStatus())) {
            stringRedisTemplate.delete(lockKey);
            return false;
        }

        record.setStatus(FeishuSyncTaskStatus.RUNNING.name());
        record.setStartedAt(LocalDateTime.now());
        save(record);
        return true;
    }

    /** 导入成功结束：写入汇总结果并释放锁。 */
    public void complete(Long taskId, ImportFeishuResponseDTO result, FeishuSyncTaskStatus status) {
        FeishuSyncTaskRecord record = requireById(taskId);
        int total = record.getTotalSteps() != null
                ? record.getTotalSteps()
                : (result.getLocations() != null ? result.getLocations().size() : 0);
        record.setStatus(status.name())
                .setImportedCount(result.getImportedCount())
                .setFailedCount(result.getFailedCount())
                .setSkippedCount(result.getSkippedCount())
                .setTotalSteps(total)
                .setCompletedSteps(total)
                .setProgressPercent(100)
                .setResult(result)
                .setErrorMessage(null)
                .setFinishedAt(LocalDateTime.now());
        save(record);
        releaseLock(taskId);
    }

    /** 执行中更新进度（细粒度 SSE / 轮询）。 */
    public void updateProgress(
            Long taskId,
            int completedSteps,
            int totalSteps,
            int successCount,
            int failedCount,
            int skippedCount) {
        FeishuSyncTaskRecord record = requireById(taskId);
        record.setCompletedSteps(completedSteps)
                .setTotalSteps(totalSteps)
                .setImportedCount(successCount)
                .setFailedCount(failedCount)
                .setSkippedCount(skippedCount)
                .setProgressPercent(calcPercent(completedSteps, totalSteps));
        save(record);
    }

    private static int calcPercent(int completed, int total) {
        if (total <= 0) {
            return completed > 0 ? 100 : 0;
        }
        return Math.min(100, (int) Math.round(completed * 100.0 / total));
    }

    /** 拉回任务成功结束。 */
    public void completePull(Long taskId, ImportFromFeishuTableResponseDTO result, FeishuSyncTaskStatus status) {
        FeishuSyncTaskRecord record = requireById(taskId);
        record.setStatus(status.name())
                .setImportedCount(result.getSuccessCount())
                .setFailedCount(result.getFailedCount())
                .setSkippedCount(result.getSkippedCount())
                .setTotalSteps(result.getTotalRows())
                .setCompletedSteps(result.getTotalRows())
                .setProgressPercent(100)
                .setPullResult(result)
                .setErrorMessage(null)
                .setFinishedAt(LocalDateTime.now());
        save(record);
        releaseLock(taskId);
    }

    /** 导入失败：记录错误信息并释放锁（MQ 可配置重试）。 */
    public void fail(Long taskId, String errorMessage) {
        FeishuSyncTaskRecord record = requireById(taskId);
        record.setStatus(FeishuSyncTaskStatus.FAILED.name())
                .setErrorMessage(errorMessage)
                .setFinishedAt(LocalDateTime.now());
        save(record);
        releaseLock(taskId);
    }

    private void save(FeishuSyncTaskRecord record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            stringRedisTemplate.opsForValue().set(
                    taskKey(record.getTaskId()),
                    json,
                    Duration.ofDays(feishuProperties.getTaskTtlDays()));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "保存任务状态失败");
        }
    }

    private void releaseLock(Long taskId) {
        stringRedisTemplate.delete(lockKey(taskId));
    }

    private static String taskKey(Long taskId) {
        return TASK_KEY_PREFIX + taskId;
    }

    private static String lockKey(Long taskId) {
        return LOCK_KEY_PREFIX + taskId;
    }
}
