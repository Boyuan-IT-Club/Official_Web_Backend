package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewBookingDTO;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
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

        slotInventoryService.syncRemainCacheFromDb(message.getSlotId());
        InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
        log.info("秒杀落库成功 requestId={}, scheduleId={}, slotId={}",
                message.getRequestId(), schedule.getScheduleId(), message.getSlotId());
        return InterviewBookingDTO.from(schedule, slot);
    }
}
