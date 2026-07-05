package club.boyuan.official.service.impl;

import club.boyuan.official.dto.*;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IInterviewBookingService;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import club.boyuan.official.service.IRecruitmentCycleService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.InterviewFineSlotTimeService;
import club.boyuan.official.service.InterviewNotificationService;
import club.boyuan.official.service.InterviewSlotInventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试预约业务实现。
 * <p>
 * 名额扣减统一委托 {@link InterviewSlotInventoryService}：DB 单条原子 UPDATE 为准，Redis 剩余名额做高并发快速拒绝。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewBookingServiceImpl implements IInterviewBookingService {

    private static final int SCHEDULE_STATUS_CANCELLED = 2;
    private static final int SCHEDULE_STATUS_ACTIVE = 1;
    private static final int SLOT_STATUS_CLOSED = 3;

    private final IInterviewSlotService interviewSlotService;
    private final IInterviewScheduleService interviewScheduleService;
    private final IResumeService resumeService;
    private final IRecruitmentCycleService recruitmentCycleService;
    private final InterviewSlotInventoryService slotInventoryService;
    private final InterviewFineSlotTimeService fineSlotTimeService;
    private final InterviewNotificationService interviewNotificationService;

    @Override
    public List<InterviewBookableSlotDTO> listBookableSlots(Integer userId, Integer cycleId, boolean resumeSubmittedOnly) {
        log.debug("开始查询可预约时段，userId={}, cycleId={}", userId, cycleId);
        validateCycleExists(cycleId);
        Resume resume = requireResume(userId, cycleId);
        if (resumeSubmittedOnly && (resume.getStatus() == null || resume.getStatus() < 2)) {
            log.warn("简历未提交，不可查看预约列表，userId={}, cycleId={}, resumeStatus={}",
                    userId, cycleId, resume.getStatus());
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_SUBMITTED_FOR_BOOKING);
        }

        LambdaQueryWrapper<InterviewSlot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSlot::getCycleId, cycleId)
                .ne(InterviewSlot::getStatus, SLOT_STATUS_CLOSED)
                .orderByAsc(InterviewSlot::getInterviewDate)
                .orderByAsc(InterviewSlot::getStartTime)
                .orderByAsc(InterviewSlot::getEndTime);

        List<InterviewSlot> slots = interviewSlotService.list(wrapper);
        slotInventoryService.warmRemainCacheForCycle(
                slots.stream().map(InterviewSlot::getSlotId).collect(Collectors.toList()));

        List<InterviewBookableSlotDTO> result = slots.stream()
                .map(InterviewBookableSlotDTO::from)
                .collect(Collectors.toList());
        long fullCount = result.stream().filter(s -> Boolean.TRUE.equals(s.getFullyBooked())).count();
        log.debug("可预约时段查询结束，cycleId={}, 共 {} 条，其中约满 {} 条", cycleId, result.size(), fullCount);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewBookingDTO createOrUpdateBooking(Integer userId, CreateInterviewBookingRequestDTO request) {
        log.info("处理面试预约，userId={}, cycleId={}, slotId={}", userId, request.getCycleId(), request.getSlotId());
        validateCycleExists(request.getCycleId());
        Resume resume = requireResume(userId, request.getCycleId());
        if (resume.getStatus() == null || resume.getStatus() < 2) {
            log.warn("简历未提交，拒绝预约，userId={}, resumeId={}", userId, resume.getResumeId());
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_SUBMITTED_FOR_BOOKING);
        }

        requireBookableSlotMetadata(request.getSlotId(), request.getCycleId());

        InterviewSchedule existing = findScheduleByResumeAndCycle(resume.getResumeId(), request.getCycleId());
        if (existing == null) {
            return createNewBooking(resume, request);
        }

        if (Objects.equals(existing.getStatus(), SCHEDULE_STATUS_CANCELLED)) {
            return reactivateCancelledBooking(existing, request);
        }

        if (Objects.equals(existing.getSlotId(), request.getSlotId())) {
            log.debug("重复预约同一时段，仅更新备注，scheduleId={}, slotId={}",
                    existing.getScheduleId(), request.getSlotId());
            if (request.getNotes() != null) {
                existing.setNotes(request.getNotes());
                interviewScheduleService.updateById(existing);
            }
            return toBookingDto(existing);
        }

        return switchExistingBooking(existing, request);
    }

    @Override
    public InterviewBookingDTO getMyBooking(Integer userId, Integer cycleId) {
        validateCycleExists(cycleId);
        Resume resume = resumeService.getResumeByUserIdAndCycleId(userId, cycleId);
        if (resume == null) {
            log.debug("无简历记录，userId={}, cycleId={}", userId, cycleId);
            return null;
        }
        InterviewSchedule schedule = findActiveScheduleByResumeAndCycle(resume.getResumeId(), cycleId);
        if (schedule == null) {
            return null;
        }
        return toBookingDto(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewBookingDTO rescheduleBooking(Integer userId, Integer scheduleId, UpdateInterviewBookingRequestDTO request) {
        log.info("处理改期，userId={}, scheduleId={}, newSlotId={}", userId, scheduleId, request.getSlotId());
        InterviewSchedule schedule = requireOwnedActiveSchedule(userId, scheduleId);
        if (Objects.equals(schedule.getSlotId(), request.getSlotId())) {
            log.debug("改期目标与原 slot 相同，scheduleId={}", scheduleId);
            if (request.getNotes() != null) {
                schedule.setNotes(request.getNotes());
                interviewScheduleService.updateById(schedule);
            }
            return toBookingDto(schedule);
        }

        requireBookableSlotMetadata(request.getSlotId(), schedule.getCycleId());
        Integer oldSlotId = schedule.getSlotId();
        if (!slotInventoryService.transferOccupancy(oldSlotId, request.getSlotId())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
        }
        schedule.setSlotId(request.getSlotId())
                .setInterviewTime(resolveFineInterviewTimeAfterOccupy(request.getSlotId()))
                .setNotes(request.getNotes())
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.updateById(schedule);
        log.info("改期完成，scheduleId={}, oldSlotId={}, newSlotId={}", scheduleId, oldSlotId, request.getSlotId());
        interviewNotificationService.enqueueBookingSuccess(scheduleId, null);
        return toBookingDto(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBooking(Integer userId, Integer scheduleId) {
        InterviewSchedule schedule = requireOwnedActiveSchedule(userId, scheduleId);
        log.info("取消预约，userId={}, scheduleId={}, slotId={}",
                userId, scheduleId, schedule.getSlotId());
        slotInventoryService.release(schedule.getSlotId());
        schedule.setStatus(SCHEDULE_STATUS_CANCELLED)
                .setInterviewTime(null)
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.updateById(schedule);
        log.info("预约已取消，scheduleId={}", scheduleId);
    }

    @Override
    public InterviewBookingAdminListResponseDTO listBookingsForAdmin(
            Integer cycleId, Boolean hasFineInterviewTime, Integer page, Integer size) {
        validateCycleExists(cycleId);

        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getCycleId, cycleId)
                .ne(InterviewSchedule::getStatus, SCHEDULE_STATUS_CANCELLED)
                .orderByDesc(InterviewSchedule::getCreatedAt);
        if (hasFineInterviewTime != null) {
            if (hasFineInterviewTime) {
                wrapper.isNotNull(InterviewSchedule::getInterviewTime);
            } else {
                wrapper.isNull(InterviewSchedule::getInterviewTime);
            }
        }

        Page<InterviewSchedule> pageResult = interviewScheduleService.page(new Page<>(page, size), wrapper);
        List<InterviewBookingDTO> list = new ArrayList<>();
        for (InterviewSchedule schedule : pageResult.getRecords()) {
            list.add(toBookingDto(schedule));
        }

        InterviewBookingAdminListResponseDTO response = new InterviewBookingAdminListResponseDTO();
        response.setTotal(pageResult.getTotal());
        response.setList(list);
        log.debug("管理员预约列表，cycleId={}, page={}, size={}, total={}",
                cycleId, page, size, pageResult.getTotal());
        return response;
    }

    private InterviewBookingDTO createNewBooking(Resume resume, CreateInterviewBookingRequestDTO request) {
        if (!slotInventoryService.tryOccupy(request.getSlotId())) {
            log.warn("首次预约占坑失败，userId={}, slotId={}", resume.getUserId(), request.getSlotId());
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
        }
        InterviewSchedule schedule = new InterviewSchedule()
                .setResumeId(resume.getResumeId())
                .setCycleId(request.getCycleId())
                .setSlotId(request.getSlotId())
                .setInterviewTime(resolveFineInterviewTimeAfterOccupy(request.getSlotId()))
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes(request.getNotes())
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.save(schedule);
        log.info("新建预约成功，scheduleId={}, resumeId={}, slotId={}",
                schedule.getScheduleId(), resume.getResumeId(), request.getSlotId());
        InterviewBookingDTO dto = toBookingDto(schedule);
        interviewNotificationService.enqueueBookingSuccess(schedule.getScheduleId(), null);
        return dto;
    }

    private InterviewBookingDTO reactivateCancelledBooking(InterviewSchedule existing,
                                                           CreateInterviewBookingRequestDTO request) {
        log.info("复用已取消的预约记录，scheduleId={}, slotId={}", existing.getScheduleId(), request.getSlotId());
        if (!slotInventoryService.tryOccupy(request.getSlotId())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
        }
        existing.setSlotId(request.getSlotId())
                .setInterviewTime(resolveFineInterviewTimeAfterOccupy(request.getSlotId()))
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes(request.getNotes())
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.updateById(existing);
        interviewNotificationService.enqueueBookingSuccess(existing.getScheduleId(), null);
        return toBookingDto(existing);
    }

    private InterviewBookingDTO switchExistingBooking(InterviewSchedule existing,
                                                      CreateInterviewBookingRequestDTO request) {
        Integer oldSlotId = existing.getSlotId();
        log.info("覆盖改约，scheduleId={}, oldSlotId={}, newSlotId={}",
                existing.getScheduleId(), oldSlotId, request.getSlotId());
        if (!slotInventoryService.transferOccupancy(oldSlotId, request.getSlotId())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL);
        }
        existing.setSlotId(request.getSlotId())
                .setInterviewTime(resolveFineInterviewTimeAfterOccupy(request.getSlotId()))
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes(request.getNotes())
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.updateById(existing);
        interviewNotificationService.enqueueBookingSuccess(existing.getScheduleId(), null);
        return toBookingDto(existing);
    }

    private LocalDateTime resolveFineInterviewTimeAfterOccupy(Integer slotId) {
        InterviewSlot slot = interviewSlotService.getById(slotId);
        return fineSlotTimeService.resolveForOccupiedSlot(slot);
    }

    private InterviewBookingDTO toBookingDto(InterviewSchedule schedule) {
        InterviewSlot slot = interviewSlotService.getById(schedule.getSlotId());
        return InterviewBookingDTO.from(schedule, slot);
    }

    private void validateCycleExists(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            log.warn("招募周期不存在，cycleId={}", cycleId);
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }

    private Resume requireResume(Integer userId, Integer cycleId) {
        Resume resume = resumeService.getResumeByUserIdAndCycleId(userId, cycleId);
        if (resume == null) {
            log.warn("简历不存在，userId={}, cycleId={}", userId, cycleId);
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        return resume;
    }

    /**
     * 仅校验 slot 元数据（存在、周期、未关闭）；是否已满交给原子占坑，避免读-写竞态下的无效拒绝。
     */
    private void requireBookableSlotMetadata(Integer slotId, Integer cycleId) {
        InterviewSlot slot = interviewSlotService.getById(slotId);
        if (slot == null) {
            log.warn("面试时段不存在，slotId={}", slotId);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_NOT_FOUND);
        }
        if (!Objects.equals(slot.getCycleId(), cycleId)) {
            log.warn("时段与周期不匹配，slotId={}, slotCycleId={}, requestCycleId={}",
                    slotId, slot.getCycleId(), cycleId);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_CYCLE_MISMATCH);
        }
        if (Objects.equals(slot.getStatus(), SLOT_STATUS_CLOSED)) {
            log.warn("面试时段已关闭，slotId={}", slotId);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_CLOSED);
        }
    }

    private InterviewSchedule findScheduleByResumeAndCycle(Integer resumeId, Integer cycleId) {
        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getResumeId, resumeId)
                .eq(InterviewSchedule::getCycleId, cycleId)
                .last("LIMIT 1");
        return interviewScheduleService.getOne(wrapper);
    }

    private InterviewSchedule findActiveScheduleByResumeAndCycle(Integer resumeId, Integer cycleId) {
        LambdaQueryWrapper<InterviewSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSchedule::getResumeId, resumeId)
                .eq(InterviewSchedule::getCycleId, cycleId)
                .ne(InterviewSchedule::getStatus, SCHEDULE_STATUS_CANCELLED)
                .last("LIMIT 1");
        return interviewScheduleService.getOne(wrapper);
    }

    private InterviewSchedule requireOwnedActiveSchedule(Integer userId, Integer scheduleId) {
        InterviewSchedule schedule = interviewScheduleService.getById(scheduleId);
        if (schedule == null) {
            log.warn("预约不存在，scheduleId={}", scheduleId);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_NOT_FOUND);
        }
        if (Objects.equals(schedule.getStatus(), SCHEDULE_STATUS_CANCELLED)) {
            log.warn("预约已取消，scheduleId={}", scheduleId);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_NOT_FOUND);
        }
        Resume resume = resumeService.getResumeById(schedule.getResumeId());
        if (resume == null || !Objects.equals(resume.getUserId(), userId)) {
            log.warn("无权操作预约，userId={}, scheduleId={}, ownerUserId={}",
                    userId, scheduleId, resume != null ? resume.getUserId() : null);
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_FORBIDDEN);
        }
        return schedule;
    }
}
