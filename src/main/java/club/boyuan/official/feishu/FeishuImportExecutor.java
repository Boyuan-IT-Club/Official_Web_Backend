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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 飞书导入「执行层」：查 MySQL → 按面试地点分桶 → 并行调飞书 API → 回写 sync_status 与 feishu_record_id。
 */
@Component
@Slf4j
public class FeishuImportExecutor {

    private static final int STATUS_ACTIVE = 1;
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
        return execute(request, null);
    }

    public ImportFeishuResponseDTO execute(ImportFeishuRequestDTO request, FeishuSyncProgressCallback progress) {
        List<InterviewSchedule> schedules = loadSchedules(request);
        if (schedules.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_NO_SCHEDULES);
        }

        PreloadContext preload = preload(schedules, request.getCycleId());
        BucketPlan plan = buildBuckets(schedules, request, preload);

        if (plan.buckets.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_MISSING,
                    "没有可导入的记录：请为 interview_slot 配置 feishu_table_url 或在请求中传入 feishuTableUrl");
        }

        ImportFeishuResponseDTO response = new ImportFeishuResponseDTO();
        response.setSkippedCount(plan.skipped);

        int totalBuckets = plan.buckets.size();
        if (progress != null) {
            progress.onProgress(0, totalBuckets, 0, 0, plan.skipped);
        }

        int concurrency = Math.max(1, feishuProperties.getParallelBucketConcurrency());
        Semaphore limit = new Semaphore(concurrency);
        AtomicInteger completedBuckets = new AtomicInteger(0);
        AtomicInteger importedAcc = new AtomicInteger(0);
        AtomicInteger failedAcc = new AtomicInteger(0);

        List<CompletableFuture<FeishuLocationImportResultDTO>> futures = new ArrayList<>();

        for (LocationBucket bucket : plan.buckets.values()) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    limit.acquire();
                    FeishuLocationImportResultDTO result = importBucket(bucket, preload.snapshotByResumeId);
                    importedAcc.addAndGet(result.getImportedCount());
                    failedAcc.addAndGet(result.getFailedCount());
                    if (progress != null) {
                        progress.onProgress(
                                completedBuckets.incrementAndGet(),
                                totalBuckets,
                                importedAcc.get(),
                                failedAcc.get(),
                                plan.skipped);
                    }
                    return result;
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
            bucket.schedules.add(schedule);
        }
        return new BucketPlan(buckets, skipped);
    }

    private FeishuLocationImportResultDTO importBucket(LocationBucket bucket,
                                                       Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId) {
        bucket.schedules.sort(Comparator.comparing(
                InterviewSchedule::getInterviewTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        FeishuLocationImportResultDTO locationResult = new FeishuLocationImportResultDTO()
                .setLocation(bucket.locationKey)
                .setTableUrl(bucket.tableUrl);

        List<ScheduledRow> createPlans = new ArrayList<>();
        List<ScheduledRow> updatePlans = new ArrayList<>();
        int rowBuildFailed = 0;

        for (InterviewSchedule schedule : bucket.schedules) {
            try {
                Map<String, Object> fields = buildRow(schedule, snapshotByResumeId);
                ScheduledRow row = new ScheduledRow(schedule.getScheduleId(), fields);
                if (StringUtils.hasText(schedule.getFeishuRecordId())) {
                    updatePlans.add(new ScheduledRow(schedule.getScheduleId(), schedule.getFeishuRecordId(), fields));
                } else {
                    createPlans.add(row);
                }
            } catch (Exception ex) {
                rowBuildFailed++;
                log.error("组装飞书行失败 scheduleId={}", schedule.getScheduleId(), ex);
            }
        }

        if (createPlans.isEmpty() && updatePlans.isEmpty()) {
            locationResult.setMessage("无可导入行")
                    .setFailedCount(bucket.schedules.size());
            return locationResult;
        }

        int imported = 0;
        try {
            if (!createPlans.isEmpty()) {
                List<Map<String, Object>> rows = createPlans.stream().map(ScheduledRow::fields).toList();
                FeishuBatchWriteResult created = feishuBitableClient.batchCreateRecords(bucket.tableUrl, rows);
                imported += created.count();
                persistCreateResults(createPlans, created.recordIds());
            }
            if (!updatePlans.isEmpty()) {
                List<FeishuBitableClient.RecordUpdate> updates = updatePlans.stream()
                        .map(r -> new FeishuBitableClient.RecordUpdate(r.recordId(), r.fields()))
                        .toList();
                FeishuBatchWriteResult updated = feishuBitableClient.batchUpdateRecords(bucket.tableUrl, updates);
                imported += updated.count();
                markSynced(updatePlans.stream().map(ScheduledRow::scheduleId).toList());
            }

            locationResult.setImportedCount(imported)
                    .setFailedCount(rowBuildFailed)
                    .setMessage(rowBuildFailed > 0 ? "部分导入成功" : "导入成功");
            return locationResult;
        } catch (BusinessException ex) {
            int failedRows = createPlans.size() + updatePlans.size() + rowBuildFailed;
            locationResult.setFailedCount(failedRows)
                    .setMessage(ex.getMessage());
            log.error("飞书导入失败 location={}, url={}", bucket.locationKey, bucket.tableUrl, ex);
            return locationResult;
        }
    }

    private void persistCreateResults(List<ScheduledRow> createPlans, List<String> recordIds) {
        for (int i = 0; i < createPlans.size(); i++) {
            String recordId = i < recordIds.size() ? recordIds.get(i) : null;
            var update = interviewScheduleService.lambdaUpdate()
                    .set(InterviewSchedule::getSyncStatus, SYNC_DONE)
                    .eq(InterviewSchedule::getScheduleId, createPlans.get(i).scheduleId());
            if (StringUtils.hasText(recordId)) {
                update.set(InterviewSchedule::getFeishuRecordId, recordId);
            }
            update.update();
        }
    }

    private void markSynced(List<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return;
        }
        interviewScheduleService.lambdaUpdate()
                .set(InterviewSchedule::getSyncStatus, SYNC_DONE)
                .in(InterviewSchedule::getScheduleId, scheduleIds)
                .update();
    }

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

    private String resolveLocationKey(InterviewSlot slot) {
        if (StringUtils.hasText(slot.getLocation())) {
            return slot.getLocation().trim();
        }
        if (Objects.equals(slot.getInterviewType(), 2)) {
            return "线上面试";
        }
        return "未指定地点";
    }

    private record PreloadContext(
            Map<Integer, InterviewSlot> slotById,
            Map<Integer, ResumeFieldReader.ResumeSnapshot> snapshotByResumeId) {
    }

    private record BucketPlan(Map<String, LocationBucket> buckets, int skipped) {
    }

    private record ScheduledRow(int scheduleId, String recordId, Map<String, Object> fields) {
        ScheduledRow(int scheduleId, Map<String, Object> fields) {
            this(scheduleId, null, fields);
        }
    }

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
