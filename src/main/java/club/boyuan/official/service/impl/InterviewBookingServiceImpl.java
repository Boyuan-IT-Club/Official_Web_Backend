package club.boyuan.official.service.impl;

import club.boyuan.official.dto.*;
import club.boyuan.official.entity.Department;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.InterviewSlot;
import club.boyuan.official.entity.RecruitmentCycle;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.DepartmentService;
import club.boyuan.official.service.IInterviewBookingService;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.service.IInterviewSlotService;
import club.boyuan.official.service.IRecruitmentCycleService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.InterviewFineSlotTimeService;
import club.boyuan.official.service.InterviewNotificationService;
import club.boyuan.official.service.InterviewSlotInventoryService;
import club.boyuan.official.service.ResumeDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ResumeDataService resumeDataService;
    private final DepartmentService departmentService;
    private final ObjectMapper objectMapper;

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
        List<Integer> requestedSlotIds = request.resolveSlotIds();
        log.info("处理面试预约，userId={}, cycleId={}, slotIds={}", userId, request.getCycleId(), requestedSlotIds);
        validateCycleExists(request.getCycleId());
        Resume resume = requireResume(userId, request.getCycleId());
        if (resume.getStatus() == null || resume.getStatus() < 2) {
            log.warn("简历未提交，拒绝预约，userId={}, resumeId={}", userId, resume.getResumeId());
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_SUBMITTED_FOR_BOOKING);
        }

        AssignmentPlan assignmentPlan = buildAssignmentPlan(resume, request.getCycleId(), requestedSlotIds);

        InterviewSchedule existing = findScheduleByResumeAndCycle(resume.getResumeId(), request.getCycleId());
        if (existing == null) {
            return createNewBooking(resume, request.getCycleId(), request.getNotes(), assignmentPlan);
        }

        if (Objects.equals(existing.getStatus(), SCHEDULE_STATUS_CANCELLED)) {
            return reactivateCancelledBooking(existing, request.getNotes(), assignmentPlan);
        }

        return switchExistingBooking(existing, request.getNotes(), assignmentPlan);
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
        List<Integer> requestedSlotIds = request.resolveSlotIds();
        log.info("处理改期，userId={}, scheduleId={}, newSlotIds={}", userId, scheduleId, requestedSlotIds);
        InterviewSchedule schedule = requireOwnedActiveSchedule(userId, scheduleId);
        Resume resume = resumeService.getResumeById(schedule.getResumeId());
        AssignmentPlan assignmentPlan = buildAssignmentPlan(resume, schedule.getCycleId(), requestedSlotIds);
        return switchExistingBooking(schedule, request.getNotes(), assignmentPlan);
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

    private InterviewBookingDTO createNewBooking(Resume resume,
                                                 Integer cycleId,
                                                 String notes,
                                                 AssignmentPlan assignmentPlan) {
        for (AssignmentCandidate candidate : assignmentPlan.candidates()) {
            Integer slotId = candidate.slot().getSlotId();
            if (!slotInventoryService.tryOccupy(slotId)) {
                log.debug("候选时段占坑失败，继续尝试下一个，userId={}, slotId={}", resume.getUserId(), slotId);
                continue;
            }
            InterviewSchedule schedule = new InterviewSchedule()
                    .setResumeId(resume.getResumeId())
                    .setCycleId(cycleId)
                    .setSlotId(slotId)
                    .setPreferredSlotIds(serializeSlotIds(assignmentPlan.requestedSlotIds()))
                    .setAssignedDeptId(candidate.assignedDeptId())
                    .setInterviewTime(resolveFineInterviewTimeAfterOccupy(slotId))
                    .setStatus(SCHEDULE_STATUS_ACTIVE)
                    .setNotes(notes)
                    .setSyncStatus(0)
                    .setNotifStatus(0);
            interviewScheduleService.save(schedule);
            log.info("新建预约成功，scheduleId={}, resumeId={}, slotId={}, assignedDeptId={}",
                    schedule.getScheduleId(), resume.getResumeId(), slotId, candidate.assignedDeptId());
            InterviewBookingDTO dto = toBookingDto(schedule);
            interviewNotificationService.enqueueBookingSuccess(schedule.getScheduleId(), null);
            return dto;
        }
        throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL, "所选面试时段均已约满");
    }

    private InterviewBookingDTO reactivateCancelledBooking(InterviewSchedule existing,
                                                           String notes,
                                                           AssignmentPlan assignmentPlan) {
        log.info("复用已取消的预约记录，scheduleId={}, candidateSlotIds={}",
                existing.getScheduleId(), assignmentPlan.requestedSlotIds());
        for (AssignmentCandidate candidate : assignmentPlan.candidates()) {
            Integer slotId = candidate.slot().getSlotId();
            if (!slotInventoryService.tryOccupy(slotId)) {
                continue;
            }
            existing.setSlotId(slotId)
                    .setPreferredSlotIds(serializeSlotIds(assignmentPlan.requestedSlotIds()))
                    .setAssignedDeptId(candidate.assignedDeptId())
                    .setInterviewTime(resolveFineInterviewTimeAfterOccupy(slotId))
                    .setStatus(SCHEDULE_STATUS_ACTIVE)
                    .setNotes(notes)
                    .setSyncStatus(0)
                    .setNotifStatus(0);
            interviewScheduleService.updateById(existing);
            interviewNotificationService.enqueueBookingSuccess(existing.getScheduleId(), null);
            return toBookingDto(existing);
        }
        throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL, "所选面试时段均已约满");
    }

    private InterviewBookingDTO switchExistingBooking(InterviewSchedule existing,
                                                      String notes,
                                                      AssignmentPlan assignmentPlan) {
        Integer oldSlotId = existing.getSlotId();
        log.info("覆盖改约，scheduleId={}, oldSlotId={}, candidateSlotIds={}",
                existing.getScheduleId(), oldSlotId, assignmentPlan.requestedSlotIds());

        for (AssignmentCandidate candidate : assignmentPlan.candidates()) {
            Integer targetSlotId = candidate.slot().getSlotId();
            if (Objects.equals(oldSlotId, targetSlotId)) {
                existing.setPreferredSlotIds(serializeSlotIds(assignmentPlan.requestedSlotIds()))
                        .setAssignedDeptId(candidate.assignedDeptId())
                        .setNotes(notes)
                        .setSyncStatus(0)
                        .setNotifStatus(0);
                interviewScheduleService.updateById(existing);
                log.debug("改约后仍命中原 slot，scheduleId={}, slotId={}", existing.getScheduleId(), oldSlotId);
                return toBookingDto(existing);
            }
            if (!slotInventoryService.transferOccupancy(oldSlotId, targetSlotId)) {
                log.debug("改约候选时段占坑失败，继续尝试下一个，scheduleId={}, targetSlotId={}",
                        existing.getScheduleId(), targetSlotId);
                continue;
            }
            existing.setSlotId(targetSlotId)
                    .setPreferredSlotIds(serializeSlotIds(assignmentPlan.requestedSlotIds()))
                    .setAssignedDeptId(candidate.assignedDeptId())
                    .setInterviewTime(resolveFineInterviewTimeAfterOccupy(targetSlotId))
                    .setStatus(SCHEDULE_STATUS_ACTIVE)
                    .setNotes(notes)
                    .setSyncStatus(0)
                    .setNotifStatus(0);
            interviewScheduleService.updateById(existing);
            log.info("改约完成，scheduleId={}, oldSlotId={}, newSlotId={}, assignedDeptId={}",
                    existing.getScheduleId(), oldSlotId, targetSlotId, candidate.assignedDeptId());
            interviewNotificationService.enqueueBookingSuccess(existing.getScheduleId(), null);
            return toBookingDto(existing);
        }
        throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_FULL, "所选面试时段均已约满");
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
    private InterviewSlot requireBookableSlotMetadata(Integer slotId, Integer cycleId) {
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
        return slot;
    }

    private AssignmentPlan buildAssignmentPlan(Resume resume, Integer cycleId, List<Integer> requestedSlotIds) {
        if (requestedSlotIds == null || requestedSlotIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "面试时段ID不能为空");
        }
        List<InterviewSlot> requestedSlots = new ArrayList<>();
        for (Integer slotId : requestedSlotIds) {
            requestedSlots.add(requireBookableSlotMetadata(slotId, cycleId));
        }
        List<Integer> preferredDeptIds = resolvePreferredDepartmentIds(resume);
        List<AssignmentCandidate> orderedCandidates = orderCandidatesByDepartment(requestedSlots, preferredDeptIds);
        if (orderedCandidates.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SLOT_NOT_FOUND);
        }
        return new AssignmentPlan(requestedSlotIds, orderedCandidates);
    }

    private List<AssignmentCandidate> orderCandidatesByDepartment(
            List<InterviewSlot> slots, List<Integer> preferredDeptIds) {
        LinkedHashMap<Integer, AssignmentCandidate> ordered = new LinkedHashMap<>();
        Integer firstPreferredDeptId = preferredDeptIds.isEmpty() ? null : preferredDeptIds.get(0);

        for (Integer deptId : preferredDeptIds) {
            for (InterviewSlot slot : slots) {
                if (Objects.equals(slot.getDeptId(), deptId)) {
                    ordered.putIfAbsent(slot.getSlotId(), new AssignmentCandidate(slot, deptId));
                }
            }
        }

        for (InterviewSlot slot : slots) {
            if (slot.getDeptId() == null) {
                ordered.putIfAbsent(slot.getSlotId(), new AssignmentCandidate(slot, firstPreferredDeptId));
            }
        }

        for (InterviewSlot slot : slots) {
            Integer assignedDeptId = slot.getDeptId() != null ? slot.getDeptId() : firstPreferredDeptId;
            ordered.putIfAbsent(slot.getSlotId(), new AssignmentCandidate(slot, assignedDeptId));
        }

        return new ArrayList<>(ordered.values());
    }

    private List<Integer> resolvePreferredDepartmentIds(Resume resume) {
        if (resume == null) {
            return Collections.emptyList();
        }
        ResumeFieldDefinition expectedDepartmentsField =
                resumeDataService.getExpectedDepartmentsFieldDefinition(resume.getCycleId());
        if (expectedDepartmentsField == null) {
            log.debug("周期未配置期望部门字段，cycleId={}", resume.getCycleId());
            return Collections.emptyList();
        }
        Map<Integer, List<String>> preferredDepartments =
                resumeDataService.getUserPreferredDepartments(List.of(resume), expectedDepartmentsField.getFieldId());
        List<String> rawDepartments = preferredDepartments.getOrDefault(resume.getUserId(), Collections.emptyList());
        if (rawDepartments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> deptIndex = buildDepartmentIndex();
        Set<Integer> ids = new LinkedHashSet<>();
        for (String raw : rawDepartments) {
            String key = normalizeDepartmentKey(raw);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Integer deptId = deptIndex.get(key);
            if (deptId != null) {
                ids.add(deptId);
            } else {
                log.debug("未能把志愿部门匹配到 department 表，resumeId={}, value={}",
                        resume.getResumeId(), raw);
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<String, Integer> buildDepartmentIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (Department department : departmentService.list()) {
            if (department == null || department.getDeptId() == null) {
                continue;
            }
            if (department.getStatus() != null && department.getStatus() == 0) {
                continue;
            }
            putDepartmentIndex(index, String.valueOf(department.getDeptId()), department.getDeptId());
            putDepartmentIndex(index, department.getDeptName(), department.getDeptId());
            putDepartmentIndex(index, department.getDeptCode(), department.getDeptId());
        }
        return index;
    }

    private void putDepartmentIndex(Map<String, Integer> index, String key, Integer deptId) {
        String normalized = normalizeDepartmentKey(key);
        if (StringUtils.hasText(normalized)) {
            index.putIfAbsent(normalized, deptId);
        }
    }

    private String normalizeDepartmentKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    private String serializeSlotIds(List<Integer> slotIds) {
        try {
            return objectMapper.writeValueAsString(slotIds);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "序列化预约候选时段失败");
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

    private record AssignmentPlan(List<Integer> requestedSlotIds, List<AssignmentCandidate> candidates) {
    }

    private record AssignmentCandidate(InterviewSlot slot, Integer assignedDeptId) {
    }
}
