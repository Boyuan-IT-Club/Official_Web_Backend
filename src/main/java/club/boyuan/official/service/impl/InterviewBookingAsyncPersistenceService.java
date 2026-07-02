package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewBookingDTO;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.mapper.InterviewSlotMapper;
import club.boyuan.official.messaging.BookingOperationType;
import club.boyuan.official.messaging.InterviewBookingMessage;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import club.boyuan.official.service.InterviewFineSlotTimeService;
import club.boyuan.official.service.InterviewSlotInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 秒杀消息消费者：将 Redis 预扣结果落库为 interview_schedule。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingAsyncPersistenceService {

    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final InterviewSlotInventoryService slotInventoryService;
    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewSlotService interviewSlotService;
    private final InterviewFineSlotTimeService fineSlotTimeService;
    private final InterviewSlotMapper interviewSlotMapper;

    /**
     * @return 落库成功后的预约 DTO；失败返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewBookingDTO persist(InterviewBookingMessage message) {
        if (!slotInventoryService.tryOccupyDbOnly(message.getSlotId())) {
            log.warn("秒杀落库 DB 占坑失败 requestId={}, slotId={}",
                    message.getRequestId(), message.getSlotId());
            return null;
        }

        InterviewSlot slotAfterOccupy = interviewSlotService.getById(message.getSlotId());
        if (slotAfterOccupy == null) {
            slotInventoryService.releaseDbOnly(message.getSlotId());
            return null;
        }
        var fineInterviewTime = fineSlotTimeService.resolveForOccupiedSlot(slotAfterOccupy);

        InterviewSchedule schedule;
        if (message.getOperationType() == BookingOperationType.REACTIVATE) {
            schedule = interviewScheduleService.getById(message.getExistingScheduleId());
            if (schedule == null) {
                slotInventoryService.releaseDbOnly(message.getSlotId());
                return null;
            }
            schedule.setSlotId(message.getSlotId())
                    .setInterviewTime(fineInterviewTime)
                    .setStatus(SCHEDULE_STATUS_ACTIVE)
                    .setNotes(message.getNotes())
                    .setSyncStatus(0)
                    .setNotifStatus(0);
            interviewScheduleService.updateById(schedule);
        } else {
            schedule = new InterviewSchedule()
                    .setResumeId(message.getResumeId())
                    .setCycleId(message.getCycleId())
                    .setSlotId(message.getSlotId())
                    .setInterviewTime(fineInterviewTime)
                    .setStatus(SCHEDULE_STATUS_ACTIVE)
                    .setNotes(message.getNotes())
                    .setSyncStatus(0)
                    .setNotifStatus(0);
            interviewScheduleService.save(schedule);
        }

        slotInventoryService.syncRemainCacheFromDb(slotAfterOccupy);
        log.info("秒杀落库成功 requestId={}, scheduleId={}, slotId={}",
                message.getRequestId(), schedule.getScheduleId(), message.getSlotId());
        return InterviewBookingDTO.from(schedule, slotAfterOccupy);
    }

    /**
     * 批量落库：把同一批消息按 slotId 分组，每组只锁行 1 次、UPDATE 1 次、批量 INSERT/UPDATE schedule，
     * 把 N 次独立事务收敛成 1 次提交，降低秒杀高峰期 interview_slot 行锁的排队等待。
     *
     * @return key=requestId，value=成功的预约 DTO；超出当批可用名额或落库失败的条目 value 为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, InterviewBookingDTO> persistBatch(List<InterviewBookingMessage> messages) {
        Map<String, InterviewBookingDTO> result = new LinkedHashMap<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }
        Map<Integer, List<InterviewBookingMessage>> bySlot = messages.stream()
                .collect(Collectors.groupingBy(InterviewBookingMessage::getSlotId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Integer, List<InterviewBookingMessage>> entry : bySlot.entrySet()) {
            result.putAll(persistSlotBatch(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Map<String, InterviewBookingDTO> persistSlotBatch(Integer slotId, List<InterviewBookingMessage> messages) {
        Map<String, InterviewBookingDTO> result = new LinkedHashMap<>();
        InterviewSlot slot = interviewSlotMapper.selectByIdForUpdate(slotId);
        if (slot == null || (slot.getStatus() != null && slot.getStatus() == 3)) {
            messages.forEach(m -> result.put(m.getRequestId(), null));
            log.warn("批量落库失败：slot 不存在或已关闭，slotId={}", slotId);
            return result;
        }

        int baseOccupied = slot.getCurrentOccupied() == null ? 0 : slot.getCurrentOccupied();
        int maxCapacity = slot.getMaxCapacity() == null ? 0 : slot.getMaxCapacity();
        int capacityLeft = Math.max(0, maxCapacity - baseOccupied);
        int admitCount = Math.min(capacityLeft, messages.size());

        List<Integer> existingScheduleIds = messages.stream()
                .limit(admitCount)
                .filter(m -> m.getOperationType() == BookingOperationType.REACTIVATE)
                .map(InterviewBookingMessage::getExistingScheduleId)
                .collect(Collectors.toList());
        Map<Integer, InterviewSchedule> existingSchedules = existingScheduleIds.isEmpty()
                ? Map.of()
                : interviewScheduleService.listByIds(existingScheduleIds).stream()
                        .collect(Collectors.toMap(InterviewSchedule::getScheduleId, s -> s));

        List<InterviewSchedule> toInsert = new ArrayList<>();
        List<InterviewSchedule> toUpdate = new ArrayList<>();
        Map<String, InterviewSchedule> pendingByRequestId = new LinkedHashMap<>();

        for (int i = 0; i < messages.size(); i++) {
            InterviewBookingMessage message = messages.get(i);
            if (i >= admitCount) {
                result.put(message.getRequestId(), null);
                continue;
            }
            LocalDateTime fineInterviewTime;
            try {
                fineInterviewTime = fineSlotTimeService.resolveForSlotIndex(slot, baseOccupied + i);
            } catch (RuntimeException e) {
                log.error("批量落库分配小时间段失败 requestId={}", message.getRequestId(), e);
                result.put(message.getRequestId(), null);
                continue;
            }

            InterviewSchedule schedule;
            if (message.getOperationType() == BookingOperationType.REACTIVATE) {
                schedule = existingSchedules.get(message.getExistingScheduleId());
                if (schedule == null) {
                    result.put(message.getRequestId(), null);
                    continue;
                }
                schedule.setSlotId(slotId)
                        .setInterviewTime(fineInterviewTime)
                        .setStatus(SCHEDULE_STATUS_ACTIVE)
                        .setNotes(message.getNotes())
                        .setSyncStatus(0)
                        .setNotifStatus(0);
                toUpdate.add(schedule);
            } else {
                schedule = new InterviewSchedule()
                        .setResumeId(message.getResumeId())
                        .setCycleId(message.getCycleId())
                        .setSlotId(slotId)
                        .setInterviewTime(fineInterviewTime)
                        .setStatus(SCHEDULE_STATUS_ACTIVE)
                        .setNotes(message.getNotes())
                        .setSyncStatus(0)
                        .setNotifStatus(0);
                toInsert.add(schedule);
            }
            pendingByRequestId.put(message.getRequestId(), schedule);
        }

        if (pendingByRequestId.isEmpty()) {
            return result;
        }

        int delta = toInsert.size() + toUpdate.size();
        int rows = interviewSlotMapper.occupyBatch(slotId, delta);
        if (rows == 0) {
            log.error("批量占坑 UPDATE 未生效（容量校验异常），slotId={}, delta={}", slotId, delta);
            pendingByRequestId.keySet().forEach(requestId -> result.put(requestId, null));
            return result;
        }
        if (!toInsert.isEmpty()) {
            interviewScheduleService.saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            interviewScheduleService.updateBatchById(toUpdate);
        }

        slot.setCurrentOccupied(baseOccupied + delta);
        slotInventoryService.syncRemainCacheFromDb(slot);

        for (Map.Entry<String, InterviewSchedule> entry : pendingByRequestId.entrySet()) {
            result.put(entry.getKey(), InterviewBookingDTO.from(entry.getValue(), slot));
        }
        log.info("批量落库成功 slotId={}, 批量大小={}, 成功={}", slotId, messages.size(), pendingByRequestId.size());
        return result;
    }
}
