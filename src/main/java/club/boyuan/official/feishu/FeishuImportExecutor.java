package club.boyuan.official.feishu;

import club.boyuan.official.config.FeishuProperties;
import club.boyuan.official.dto.FeishuLocationImportResultDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.ResumeMapper;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 飞书导入「执行层」：查 MySQL → 按面试地点分桶 → 并行调飞书 API → 回写 sync_status。
 *
 * <p>{@link #execute} 主流程四步：
 * <ol>
 *   <li>{@link #loadSchedules} — 查出本周期待同步的 interview_schedule</li>
 *   <li>{@link #preload} — 一次性批量加载 slot、简历字段，避免 N+1</li>
 *   <li>{@link #buildBuckets} — 按地点（location）分组，每组对应一张飞书表 URL</li>
 *   <li>并行 {@link #importBucket} — 每组调一次 batch_create，成功则标记 sync_status=1</li>
 * </ol>
 *
 * <p>「桶 / Bucket」= 同一面试地点 + 同一张飞书多维表格下的多条 schedule。
 */
@Component
@Slf4j
public class FeishuImportExecutor {

    /** interview_schedule.status：有效预约 */
    private static final int STATUS_ACTIVE = 1;
    /** interview_schedule.sync_status：已写入飞书 */
    private static final int SYNC_DONE = 1;

    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewSlotService interviewSlotService;
    private final ResumeMapper resumeMapper;
    private final ResumeFieldReader resumeFieldReader;
    private final FeishuBitableClient feishuBitableClient;
    private final FeishuProperties feishuProperties;
    private final Executor feishuBucketExecutor;

    public FeishuImportExecutor(IInterviewScheduleService interviewScheduleService,
                                IInterviewSlotService interviewSlotService,
                                ResumeMapper resumeMapper,
                                ResumeFieldReader resumeFieldReader,
                                FeishuBitableClient feishuBitableClient,
                                FeishuProperties feishuProperties,
                                @Qualifier("feishuBucketExecutor") Executor feishuBucketExecutor) {
        this.interviewScheduleService = interviewScheduleService;
        this.interviewSlotService = interviewSlotService;
        this.resumeMapper = resumeMapper;
        this.resumeFieldReader = resumeFieldReader;
        this.feishuBitableClient = feishuBitableClient;
        this.feishuProperties = feishuProperties;
        this.feishuBucketExecutor = feishuBucketExecutor;
    }

    public ImportFeishuResponseDTO execute(ImportFeishuRequestDTO request) {
        // --- 1. 查待导入的面试安排 ---
        List<InterviewSchedule> schedules = loadSchedules(request);
        if (schedules.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_NO_SCHEDULES);
        }

        // --- 2. 批量预加载 slot、简历（一次 SQL，非逐条查）---
        PreloadContext preload = preload(schedules, request.getCycleId());
        // --- 3. 按地点分桶；无 URL 的 schedule 计入 skipped ---
        BucketPlan plan = buildBuckets(schedules, request, preload);

        if (plan.buckets.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_MISSING,
                    "没有可导入的记录：请为 interview_slot 配置 feishu_table_url 或在请求中传入 feishuTableUrl");
        }

        ImportFeishuResponseDTO response = new ImportFeishuResponseDTO();
        response.setSkippedCount(plan.skipped);

        // --- 4. 每个地点桶并行写飞书（Semaphore 限制同时请求数，防触发飞书限流）---
        int concurrency = Math.max(1, feishuProperties.getParallelBucketConcurrency());
        Semaphore limit = new Semaphore(concurrency);
        List<CompletableFuture<FeishuLocationImportResultDTO>> futures = new ArrayList<>();

        for (LocationBucket bucket : plan.buckets.values()) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    limit.acquire();
                    return importBucket(bucket, preload.snapshotByResumeId);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return failedLocationResult(bucket, "并行导入被中断");
                } finally {
                    limit.release();
                }
            }, feishuBucketExecutor));
        }

        List<FeishuLocationImportResultDTO> locationResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        for (FeishuLocationImportResultDTO locationResult : locationResults) {
            response.getLocations().add(locationResult);
            response.setImportedCount(response.getImportedCount() + locationResult.getImportedCount());
            response.setFailedCount(response.getFailedCount() + locationResult.getFailedCount());
        }

        log.info("飞书导入完成 cycleId={}, imported={}, failed={}, skipped={}, buckets={}",
                request.getCycleId(), response.getImportedCount(), response.getFailedCount(),
                response.getSkippedCount(), plan.buckets.size());
        return response;
    }

    /**
     * 加载待导入的面试安排。
     * forceUpdate=false 时只拉 sync_status=0（未同步）；true 时允许重复导入（飞书侧会追加行）。
     */
    private List<InterviewSchedule> loadSchedules(ImportFeishuRequestDTO request) {
        boolean forceUpdate = Boolean.TRUE.equals(request.getForceUpdate());
        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getCycleId, request.getCycleId())
                .eq(InterviewSchedule::getStatus, STATUS_ACTIVE);
        if (!forceUpdate) {
            wrapper.eq(InterviewSchedule::getSyncStatus, 0);
        }
        if (request.getSlotId() != null) {
            wrapper.eq(InterviewSchedule::getSlotId, request.getSlotId());
        }
        return interviewScheduleService.list(wrapper);
    }

    /** 根据 schedule 里出现的 slotId、resumeId，批量查 slot 表与简历 EAV 字段。 */
    private PreloadContext preload(List<InterviewSchedule> schedules, Integer cycleId) {
        List<Integer> slotIds = schedules.stream()
                .map(InterviewSchedule::getSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Integer> resumeIds = schedules.stream()
                .map(InterviewSchedule::getResumeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, InterviewSlot> slotById = slotIds.isEmpty()
                ? Map.of()
                : interviewSlotService.listByIds(slotIds).stream()
                .collect(Collectors.toMap(InterviewSlot::getSlotId, Function.identity(), (a, b) -> a));

        Map<Integer, Resume> resumeById = resumeIds.isEmpty()
                ? Map.of()
                : resumeMapper.selectBatchIds(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getResumeId, Function.identity(), (a, b) -> a));

        Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId =
                resumeFieldReader.readSnapshots(resumeById.values(), cycleId);

        return new PreloadContext(slotById, snapshotByResumeId);
    }

    /**
     * 按「面试地点」分桶：同一 location 的 schedule 进同一个桶，共享一张飞书表。
     * <p>LinkedHashMap 保证返回结果里地点顺序稳定（便于日志与前端展示）。
     * <p>tableUrl 优先用请求里的 feishuTableUrl，否则用 interview_slot.feishu_table_url。
     */
    private BucketPlan buildBuckets(List<InterviewSchedule> schedules,
                                    ImportFeishuRequestDTO request,
                                    PreloadContext preload) {
        Map<String, LocationBucket> buckets = new LinkedHashMap<>();
        int skipped = 0;

        for (InterviewSchedule schedule : schedules) {
            InterviewSlot slot = preload.slotById.get(schedule.getSlotId());
            if (slot == null) {
                skipped++;
                continue;
            }
            String locationKey = resolveLocationKey(slot);
            String tableUrl = resolveTableUrl(request, slot);
            if (!StringUtils.hasText(tableUrl)) {
                log.warn("跳过 scheduleId={}，地点 {} 未配置 feishuTableUrl", schedule.getScheduleId(), locationKey);
                skipped++;
                continue;
            }

            LocationBucket bucket = buckets.computeIfAbsent(locationKey, k -> new LocationBucket(locationKey, tableUrl));
            if (!Objects.equals(bucket.tableUrl, tableUrl)) {
                log.warn("地点 {} 存在多个不同的飞书表格 URL，使用首次配置的 {}", locationKey, bucket.tableUrl);
            }
            bucket.schedules.add(schedule);
        }
        return new BucketPlan(buckets, skipped);
    }

    /**
     * 单个地点桶：组装飞书行 → batch_create → 成功则批量更新 sync_status。
     * 行组装失败只记 failed，不阻断同桶其他行；整批 API 失败则该桶全部算 failed。
     */
    private FeishuLocationImportResultDTO importBucket(LocationBucket bucket,
                                                       Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId) {
        bucket.schedules.sort(Comparator.comparing(
                InterviewSchedule::getInterviewTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        FeishuLocationImportResultDTO locationResult = new FeishuLocationImportResultDTO()
                .setLocation(bucket.locationKey)
                .setTableUrl(bucket.tableUrl);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Integer> successScheduleIds = new ArrayList<>();
        int rowBuildFailed = 0;

        for (InterviewSchedule schedule : bucket.schedules) {
            try {
                rows.add(buildRow(schedule, snapshotByResumeId));
                successScheduleIds.add(schedule.getScheduleId());
            } catch (Exception ex) {
                rowBuildFailed++;
                log.error("组装飞书行失败 scheduleId={}", schedule.getScheduleId(), ex);
            }
        }

        if (rows.isEmpty()) {
            locationResult.setMessage("无可导入行")
                    .setFailedCount(bucket.schedules.size());
            return locationResult;
        }

        try {
            int imported = feishuBitableClient.batchCreateRecords(bucket.tableUrl, rows);
            markSynced(successScheduleIds);
            locationResult.setImportedCount(imported)
                    .setFailedCount(rowBuildFailed)
                    .setMessage(rowBuildFailed > 0 ? "部分导入成功" : "导入成功");
            return locationResult;
        } catch (BusinessException ex) {
            locationResult.setFailedCount(rows.size() + rowBuildFailed)
                    .setMessage(ex.getMessage());
            log.error("飞书导入失败 location={}, url={}", bucket.locationKey, bucket.tableUrl, ex);
            return locationResult;
        }
    }

    /** 飞书写入成功后，把对应 schedule 标为已同步，避免下次默认导入重复拉取。 */
    private void markSynced(List<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return;
        }
        interviewScheduleService.lambdaUpdate()
                .set(InterviewSchedule::getSyncStatus, SYNC_DONE)
                .in(InterviewSchedule::getScheduleId, scheduleIds)
                .update();
    }

    /** 把一条面试安排 + 简历快照，映射为飞书多维表格「字段名 → 值」。 */
    private Map<String, Object> buildRow(InterviewSchedule schedule,
                                         Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId) {
        ResumeFieldReader.ResumeSnapshot snapshot = snapshotByResumeId.getOrDefault(
                schedule.getResumeId(), ResumeFieldReader.ResumeSnapshot.empty());

        Map<String, Object> fields = new HashMap<>();
        fields.put(FeishuBitableColumns.NAME, snapshot.name());
        fields.put(FeishuBitableColumns.INTENDED_DEPT, snapshot.intendedDepartments());
        fields.put(FeishuBitableColumns.GRADE, snapshot.grade());
        fields.put(FeishuBitableColumns.MAJOR, snapshot.major());
        fields.put(FeishuBitableColumns.SELF_INTRO, snapshot.selfIntroduction());
        // 以下列在飞书表里由面试官现场填写，导入时留空
        fields.put(FeishuBitableColumns.QUESTION_ONE, "");
        fields.put(FeishuBitableColumns.QUESTION_TWO, "");
        fields.put(FeishuBitableColumns.QUESTION_THREE, "");
        fields.put(FeishuBitableColumns.EVALUATION, "");
        fields.put(FeishuBitableColumns.RESUME_SCORE, snapshot.resumeScore());
        fields.put(FeishuBitableColumns.PRESELECT, "");
        fields.put(FeishuBitableColumns.ADJUSTABLE, "");
        fields.put(FeishuBitableColumns.RECORDER, "");
        return fields;
    }

    private FeishuLocationImportResultDTO failedLocationResult(LocationBucket bucket, String message) {
        return new FeishuLocationImportResultDTO()
                .setLocation(bucket.locationKey)
                .setTableUrl(bucket.tableUrl)
                .setFailedCount(bucket.schedules.size())
                .setMessage(message);
    }

    private String resolveTableUrl(ImportFeishuRequestDTO request, InterviewSlot slot) {
        if (StringUtils.hasText(request.getFeishuTableUrl())) {
            return request.getFeishuTableUrl().trim();
        }
        return slot.getFeishuTableUrl();
    }

    /** 分桶键：优先 slot.location；线上面试无地点时用固定文案。 */
    private String resolveLocationKey(InterviewSlot slot) {
        if (StringUtils.hasText(slot.getLocation())) {
            return slot.getLocation().trim();
        }
        if (Objects.equals(slot.getInterviewType(), 2)) {
            return "线上面试";
        }
        return "未指定地点";
    }

    /** 预加载结果：slotId → 场次信息；resumeId → 姓名/专业等展示字段。 */
    private record PreloadContext(
            Map<Integer, InterviewSlot> slotById,
            Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId) {
    }

    /** 分桶结果 + 因缺 slot/缺 URL 而跳过的条数。 */
    private record BucketPlan(Map<String, LocationBucket> buckets, int skipped) {
    }

    /** 同一面试地点下、写入同一张飞书表的一批 schedule。 */
    static final class LocationBucket {
        private final String locationKey;
        private final String tableUrl;
        private final List<InterviewSchedule> schedules = new ArrayList<>();

        LocationBucket(String locationKey, String tableUrl) {
            this.locationKey = locationKey;
            this.tableUrl = tableUrl;
        }
    }
}
