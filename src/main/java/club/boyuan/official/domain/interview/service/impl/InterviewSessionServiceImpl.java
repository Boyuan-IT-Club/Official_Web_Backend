package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.CreateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试场次服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSession>
        implements IInterviewSessionService {

    private static final int STATUS_AVAILABLE = 1;
    private static final int DEFAULT_DURATION_MINUTES = 10;
    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final IRecruitmentCycleService recruitmentCycleService;
    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final DepartmentMapper departmentMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;

    @Override
    public InterviewSession createSession(CreateInterviewSessionRequestDTO request) {
        validateCycleExists(request.getCycleId());

        InterviewTimeSlot timeSlot = interviewTimeSlotService.getById(request.getTimeSlotId());
        if (timeSlot == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_NOT_FOUND);
        }
        if (!Objects.equals(timeSlot.getCycleId(), request.getCycleId())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_CYCLE_MISMATCH);
        }
        if (departmentMapper.selectById(request.getDeptId()) == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND);
        }

        InterviewSession session = new InterviewSession()
                .setCycleId(request.getCycleId())
                .setTimeSlotId(request.getTimeSlotId())
                .setDeptId(request.getDeptId())
                .setLocation(request.getLocation())
                .setCapacity(request.getCapacity())
                .setCurrentOccupied(0)
                .setInterviewDurationMinutes(
                        request.getInterviewDurationMinutes() == null
                                ? DEFAULT_DURATION_MINUTES : request.getInterviewDurationMinutes())
                .setStatus(STATUS_AVAILABLE);
        save(session);
        log.info("创建面试场次成功，sessionId={}, cycleId={}, timeSlotId={}, deptId={}, capacity={}",
                session.getSessionId(), session.getCycleId(), session.getTimeSlotId(),
                session.getDeptId(), session.getCapacity());
        return session;
    }

    @Override
    public InterviewSession updateSession(Integer sessionId, UpdateInterviewSessionRequestDTO request) {
        InterviewSession session = requireSession(sessionId);
        if (request.getLocation() != null) {
            session.setLocation(request.getLocation());
        }
        if (request.getCapacity() != null) {
            int occupied = session.getCurrentOccupied() == null ? 0 : session.getCurrentOccupied();
            if (request.getCapacity() < occupied) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_FULL);
            }
            session.setCapacity(request.getCapacity());
        }
        if (request.getInterviewDurationMinutes() != null) {
            session.setInterviewDurationMinutes(request.getInterviewDurationMinutes());
        }
        if (request.getStatus() != null) {
            session.setStatus(request.getStatus());
        }
        updateById(session);
        log.info("更新面试场次成功，sessionId={}", sessionId);
        return session;
    }

    @Override
    public void deleteSession(Integer sessionId) {
        requireSession(sessionId);
        Long scheduleCount = interviewScheduleMapper.selectCount(
                new LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewSchedule>()
                        .eq(club.boyuan.official.persistence.entity.InterviewSchedule::getSessionId, sessionId)
                        .eq(club.boyuan.official.persistence.entity.InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_HAS_SCHEDULE);
        }
        removeById(sessionId);
        log.info("删除面试场次成功，sessionId={}", sessionId);
    }

    @Override
    public List<InterviewSessionDTO> listSessionDTOs(Integer cycleId, Integer deptId, boolean onlyAvailable) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getCycleId, cycleId);
        if (deptId != null) {
            wrapper.eq(InterviewSession::getDeptId, deptId);
        }
        if (onlyAvailable) {
            wrapper.eq(InterviewSession::getStatus, STATUS_AVAILABLE);
        }
        List<InterviewSession> sessions = list(wrapper);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, InterviewTimeSlot> timeSlotMap = loadTimeSlots(sessions);
        Map<Integer, String> deptNameMap = loadDeptNames(sessions);

        return sessions.stream()
                .map(s -> buildDTO(s, timeSlotMap.get(s.getTimeSlotId()), deptNameMap.get(s.getDeptId())))
                .filter(dto -> !onlyAvailable || dto.getRemaining() > 0)
                .collect(Collectors.toList());
    }

    @Override
    public InterviewSessionDTO toDTO(InterviewSession session) {
        if (session == null) {
            return null;
        }
        InterviewTimeSlot timeSlot = interviewTimeSlotService.getById(session.getTimeSlotId());
        Department dept = departmentMapper.selectById(session.getDeptId());
        return buildDTO(session, timeSlot, dept == null ? null : dept.getDeptName());
    }

    private InterviewSessionDTO buildDTO(InterviewSession session, InterviewTimeSlot timeSlot, String deptName) {
        InterviewSessionDTO dto = new InterviewSessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setCycleId(session.getCycleId());
        dto.setTimeSlotId(session.getTimeSlotId());
        dto.setDeptId(session.getDeptId());
        dto.setDeptName(deptName);
        dto.setLocation(session.getLocation());
        dto.setCapacity(session.getCapacity());
        int occupied = session.getCurrentOccupied() == null ? 0 : session.getCurrentOccupied();
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        dto.setCurrentOccupied(occupied);
        dto.setRemaining(Math.max(0, capacity - occupied));
        dto.setInterviewDurationMinutes(session.getInterviewDurationMinutes());
        dto.setStatus(session.getStatus());
        if (timeSlot != null) {
            dto.setSlotName(timeSlot.getSlotName());
            dto.setInterviewDate(timeSlot.getInterviewDate());
            dto.setStartTime(timeSlot.getStartTime());
            dto.setEndTime(timeSlot.getEndTime());
        }
        return dto;
    }

    private Map<Integer, InterviewTimeSlot> loadTimeSlots(List<InterviewSession> sessions) {
        List<Integer> ids = sessions.stream()
                .map(InterviewSession::getTimeSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return interviewTimeSlotService.listByIds(ids).stream()
                .collect(Collectors.toMap(InterviewTimeSlot::getTimeSlotId, ts -> ts, (a, b) -> a));
    }

    private Map<Integer, String> loadDeptNames(List<InterviewSession> sessions) {
        List<Integer> ids = sessions.stream()
                .map(InterviewSession::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return departmentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName, (a, b) -> a));
    }

    private InterviewSession requireSession(Integer sessionId) {
        InterviewSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_NOT_FOUND);
        }
        return session;
    }

    private void validateCycleExists(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }
}
