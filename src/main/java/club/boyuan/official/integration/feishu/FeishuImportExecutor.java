package club.boyuan.official.integration.feishu;

import club.boyuan.official.infra.config.FeishuProperties;
import club.boyuan.official.integration.feishu.dto.FeishuLocationImportResultDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuResponseDTO;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSlotService;
import club.boyuan.official.domain.interview.service.ILocationTableService;
import club.boyuan.official.domain.interview.service.impl.LocationTableServiceImpl;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
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
    private final InterviewSessionMapper interviewSessionMapper;
    private final ILocationTableService locationTableService;
    private final ResumeMapper resumeMapper;
    private final ResumeFieldReader resumeFieldReader;
    private final FeishuBitableClient feishuBitableClient;
    private final FeishuProperties feishuProperties;
    private final Executor feishuBucketExecutor;

    public FeishuImportExecutor(IInterviewScheduleService interviewScheduleService,
                                IInterviewSlotService interviewSlotService,
                                InterviewSessionMapper interviewSessionMapper,
                                ILocationTableService locationTableService,
                                ResumeMapper resumeMapper,
                                ResumeFieldReader resumeFieldReader,
                                FeishuBitableClient feishuBitableClient,
                                FeishuProperties feishuProperties,
                                @Qualifier("feishuBucketExecutor") Executor feishuBucketExecutor) {
        this.interviewScheduleService = interviewScheduleService;
        this.interviewSlotService = interviewSlotService;
        this.interviewSessionMapper = interviewSessionMapper;
        this.locationTableService = locationTableService;
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
                    "没有可导入的记录：请先在「飞书同步」页为各面试地点配置多维表格链接，或在请求中传入 feishuTableUrl 以把所有地点合并推到同一张表");
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
        // 方案B 的地点在 interview_session 上；slot 分支只为兼容还带 slot_id 的历史排期而保留
        List<Integer> sessionIds = schedules.stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

        Map<Integer, InterviewSession> sessionById = sessionIds.isEmpty()
                ? Map.of()
                : interviewSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(InterviewSession::getSessionId, Function.identity(), (a, b) -> a));

        return new PreloadContext(slotById, sessionById, locationTableService.urlMapOf(cycleId),
                snapshotByResumeId);
    }

    private BucketPlan buildBuckets(List<InterviewSchedule> schedules,
                                    ImportFeishuRequestDTO request,
                                    PreloadContext preload) {
        Map<String, LocationBucket> buckets = new LinkedHashMap<>();
        int skipped = 0;

        for (InterviewSchedule schedule : schedules) {
            InterviewSession session = schedule.getSessionId() == null
                    ? null
                    : preload.sessionById.get(schedule.getSessionId());
            InterviewSlot slot = schedule.getSlotId() == null
                    ? null
                    : preload.slotById.get(schedule.getSlotId());
            if (session == null && slot == null) {
                log.warn("跳过 scheduleId={}：既没有场次也没有旧时段，无法判断地点", schedule.getScheduleId());
                skipped++;
                continue;
            }
            String locationKey = resolveLocationKey(session, slot);
            String tableUrl = resolveTableUrl(request, locationKey, preload.tableUrlByLocation, slot);
            if (!StringUtils.hasText(tableUrl)) {
                log.warn("跳过 scheduleId={}，地点「{}」未配置飞书表格链接", schedule.getScheduleId(), locationKey);
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
            // 写记录前先对齐表结构:飞书不会自动建列,写不存在的列名会整批
            // FieldNameNotFound。管理员常给一张只有默认「文本」列的空表,缺列自动补建。
            // 用实际要写的所有列名(取自本批行的 key 并集),而不是写死清单 ——
            // 将来 buildFields 增删列这里自动跟随。
            java.util.Set<String> requiredCols = new java.util.LinkedHashSet<>();
            createPlans.forEach(r -> requiredCols.addAll(r.fields().keySet()));
            updatePlans.forEach(r -> requiredCols.addAll(r.fields().keySet()));
            java.util.Map<String, Integer> colTypes = java.util.Map.of();
            if (!requiredCols.isEmpty()) {
                colTypes = feishuBitableClient.ensureFieldsExist(bucket.tableUrl, requiredCols);
            }
            // 值也要按列的真实类型转 —— 自动建的列是文本，而 resumeScore 是 int，
            // 往文本列写数字会整批 TextFieldConvFail（线上实测 code 1254060）
            final java.util.Map<String, Integer> types = colTypes;
            if (!createPlans.isEmpty()) {
                List<Map<String, Object>> rows = createPlans.stream()
                        .map(r -> FeishuBitableClient.coerceRow(r.fields(), types))
                        .toList();
                FeishuBatchWriteResult created = feishuBitableClient.batchCreateRecords(bucket.tableUrl, rows);
                imported += created.count();
                persistCreateResults(createPlans, created.recordIds());
            }
            if (!updatePlans.isEmpty()) {
                List<FeishuBitableClient.RecordUpdate> updates = updatePlans.stream()
                        .map(r -> new FeishuBitableClient.RecordUpdate(
                                r.recordId(), FeishuBitableClient.coerceRow(r.fields(), types)))
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

    /**
     * 取该地点该用哪张飞书表格。优先级：
     *   1. 请求里显式给的 URL —— 这是「覆盖」语义：所有地点都推到同一张表，会抹平分桶，仅供临时合表使用
     *   2. 「周期 × 地点」配置表（正常路径）
     *   3. 旧 interview_slot.feishu_table_url —— 只为还带 slot_id 的历史排期兜底
     */
    private String resolveTableUrl(ImportFeishuRequestDTO request,
                                   String locationKey,
                                   Map<String, String> tableUrlByLocation,
                                   InterviewSlot slot) {
        if (StringUtils.hasText(request.getFeishuTableUrl())) {
            return request.getFeishuTableUrl().trim();
        }
        String configured = tableUrlByLocation.get(locationKey);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return slot == null ? null : slot.getFeishuTableUrl();
    }

    /**
     * 分桶键。方案B 的地点真源是 interview_session.location；
     * 只有还带 slot_id 的历史排期才回退到旧时段表。
     * 规整逻辑与 {@link LocationTableServiceImpl#normalize} 共用，
     * 否则配置里的地点和分桶出来的地点会对不上。
     */
    private String resolveLocationKey(InterviewSession session, InterviewSlot slot) {
        if (session != null && StringUtils.hasText(session.getLocation())) {
            return LocationTableServiceImpl.normalize(session.getLocation());
        }
        if (slot != null) {
            if (StringUtils.hasText(slot.getLocation())) {
                return LocationTableServiceImpl.normalize(slot.getLocation());
            }
            if (Objects.equals(slot.getInterviewType(), 2)) {
                return "线上面试";
            }
        }
        return LocationTableServiceImpl.FALLBACK_LOCATION;
    }

    private record PreloadContext(
            Map<Integer, InterviewSlot> slotById,
            Map<Integer, InterviewSession> sessionById,
            Map<String, String> tableUrlByLocation,
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
