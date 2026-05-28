package club.boyuan.official.service.impl;

import club.boyuan.official.dto.FeishuLocationImportResultDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ImportFeishuResponseDTO;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.feishu.FeishuBitableClient;
import club.boyuan.official.feishu.FeishuBitableColumns;
import club.boyuan.official.feishu.ResumeFieldReader;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.InterviewFeishuImportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewFeishuImportServiceImpl implements InterviewFeishuImportService {

    private static final int STATUS_ACTIVE = 1;
    private static final int SYNC_DONE = 1;

    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewSlotService interviewSlotService;
    private final IResumeService resumeService;
    private final ResumeFieldReader resumeFieldReader;
    private final FeishuBitableClient feishuBitableClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportFeishuResponseDTO importSchedules(ImportFeishuRequestDTO request) {
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
        List<InterviewSchedule> schedules = interviewScheduleService.list(wrapper);
        if (schedules.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_NO_SCHEDULES);
        }

        Map<String, LocationBucket> buckets = new LinkedHashMap<>();
        int skipped = 0;

        for (InterviewSchedule schedule : schedules) {
            InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
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

        if (buckets.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_MISSING,
                    "没有可导入的记录：请为 interview_slot 配置 feishu_table_url 或在请求中传入 feishuTableUrl");
        }

        ImportFeishuResponseDTO response = new ImportFeishuResponseDTO();
        response.setSkippedCount(skipped);

        for (LocationBucket bucket : buckets.values()) {
            bucket.schedules.sort(Comparator.comparing(
                    InterviewSchedule::getInterviewTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            List<Map<String, Object>> rows = new ArrayList<>();
            List<InterviewSchedule> successSchedules = new ArrayList<>();

            for (InterviewSchedule schedule : bucket.schedules) {
                try {
                    rows.add(buildRow(request.getCycleId(), schedule));
                    successSchedules.add(schedule);
                } catch (Exception ex) {
                    log.error("组装飞书行失败 scheduleId={}", schedule.getScheduleId(), ex);
                    response.setFailedCount(response.getFailedCount() + 1);
                }
            }

            FeishuLocationImportResultDTO locationResult = new FeishuLocationImportResultDTO()
                    .setLocation(bucket.locationKey)
                    .setTableUrl(bucket.tableUrl);

            if (rows.isEmpty()) {
                locationResult.setMessage("无可导入行").setFailedCount(bucket.schedules.size());
                response.getLocations().add(locationResult);
                continue;
            }

            try {
                int imported = feishuBitableClient.batchCreateRecords(bucket.tableUrl, rows);
                locationResult.setImportedCount(imported);
                response.setImportedCount(response.getImportedCount() + imported);

                for (InterviewSchedule schedule : successSchedules) {
                    schedule.setSyncStatus(SYNC_DONE);
                    interviewScheduleService.updateById(schedule);
                }
                locationResult.setMessage("导入成功");
            } catch (BusinessException ex) {
                locationResult.setFailedCount(rows.size());
                locationResult.setMessage(ex.getMessage());
                response.setFailedCount(response.getFailedCount() + rows.size());
                log.error("飞书导入失败 location={}, url={}", bucket.locationKey, bucket.tableUrl, ex);
            }

            response.getLocations().add(locationResult);
        }

        log.info("飞书导入完成 cycleId={}, imported={}, failed={}, skipped={}",
                request.getCycleId(), response.getImportedCount(), response.getFailedCount(), skipped);
        return response;
    }

    private Map<String, Object> buildRow(Integer cycleId, InterviewSchedule schedule) {
        Resume resume = resumeService.getResumeById(schedule.getResumeId());
        ResumeFieldReader.ResumeSnapshot snapshot = resumeFieldReader.readSnapshot(resume, cycleId);

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

    private static final class LocationBucket {
        private final String locationKey;
        private final String tableUrl;
        private final List<InterviewSchedule> schedules = new ArrayList<>();

        private LocationBucket(String locationKey, String tableUrl) {
            this.locationKey = locationKey;
            this.tableUrl = tableUrl;
        }
    }
}
