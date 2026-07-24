package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.interview.service.ISessionAssignmentService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewPreference;
import club.boyuan.official.persistence.entity.InterviewPreferenceTime;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceTimeMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试场次分配实现（方案B）。
 * <p>
 * 每人只面一场：先试第一志愿部门的可用场次，满了降级到第二志愿，都不行进待调剂。
 * 容量按"场次(部门×时间窗×地点)"计；场次时间窗按 {@code interviewDurationMinutes} 细分到每人精确时刻。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAssignmentServiceImpl implements ISessionAssignmentService {

    private static final int RESUME_STATUS_SUBMITTED = 2;
    private static final int SESSION_STATUS_AVAILABLE = 1;
    private static final int SCHEDULE_STATUS_ACTIVE = 1;
    private static final int DEFAULT_DURATION_MINUTES = 10;

    private final IRecruitmentCycleService recruitmentCycleService;
    private final IInterviewPreferenceService interviewPreferenceService;
    private final InterviewPreferenceTimeMapper preferenceTimeMapper;
    private final IInterviewSessionService interviewSessionService;
    private final InterviewSessionMapper interviewSessionMapper;
    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final IResumeService resumeService;
    private final ResumeDataService resumeDataService;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public SessionAssignmentResultDTO assign(Integer cycleId) {
        validateCycleExists(cycleId);
        log.info("开始为周期 {} 执行场次分配", cycleId);

        Map<Integer, Resume> resumeById = loadSubmittedResumes(cycleId);
        Set<Integer> alreadyScheduled = loadActivelyScheduledResumeIds(cycleId);

        List<InterviewPreference> preferences = interviewPreferenceService.list(
                new LambdaQueryWrapper<InterviewPreference>().eq(InterviewPreference::getCycleId, cycleId));
        Map<Integer, List<Integer>> acceptedTimeSlotIds = loadAcceptedTimeSlotIds(
                preferences.stream().map(InterviewPreference::getResumeId).collect(Collectors.toList()));

        Map<Integer, List<SessionState>> statesByDept = loadSessionStates(cycleId);
        Map<Integer, String> deptNames = loadAllDeptNames();

        // 候选人：已提交简历 + 已填志愿 + 尚未分配。约束越紧（可接受时间窗越少）越优先。
        List<InterviewPreference> candidates = preferences.stream()
                .filter(p -> resumeById.containsKey(p.getResumeId()))
                .filter(p -> !alreadyScheduled.contains(p.getResumeId()))
                .sorted(Comparator
                        .comparingInt((InterviewPreference p) ->
                                acceptedTimeSlotIds.getOrDefault(p.getResumeId(), List.of()).size())
                        .thenComparing(InterviewPreference::getResumeId))
                .collect(Collectors.toList());

        SessionAssignmentResultDTO result = new SessionAssignmentResultDTO();
        result.setCycleId(cycleId);
        result.setAssignedAt(LocalDateTime.now());

        List<SessionState> touchedStates = new ArrayList<>();

        for (InterviewPreference pref : candidates) {
            Resume resume = resumeById.get(pref.getResumeId());
            List<Integer> acceptable = acceptedTimeSlotIds.getOrDefault(pref.getResumeId(), List.of());

            if (acceptable.isEmpty()) {
                result.getUnassigned().add(buildUnassigned(pref, resume, deptNames, "未勾选可接受的时间窗"));
                continue;
            }

            SessionState chosen = null;
            int matchedChoice = 0;
            if (pref.getFirstDeptId() != null) {
                chosen = pickSession(statesByDept.get(pref.getFirstDeptId()), acceptable);
                if (chosen != null) {
                    matchedChoice = 1;
                }
            }
            if (chosen == null && pref.getSecondDeptId() != null) {
                chosen = pickSession(statesByDept.get(pref.getSecondDeptId()), acceptable);
                if (chosen != null) {
                    matchedChoice = 2;
                }
            }

            if (chosen == null) {
                result.getUnassigned().add(buildUnassigned(pref, resume, deptNames,
                        "志愿部门的可选场次已满或无匹配时段"));
                continue;
            }

            int index = chosen.occupyNext();
            if (!touchedStates.contains(chosen)) {
                touchedStates.add(chosen);
            }
            InterviewSchedule schedule = persistSchedule(resume, chosen, index, matchedChoice);
            result.getAssigned().add(buildAssigned(schedule, resume, chosen, matchedChoice, deptNames));
        }

        persistOccupancy(touchedStates);

        result.setAssignedCount(result.getAssigned().size());
        result.setUnassignedCount(result.getUnassigned().size());
        log.info("周期 {} 场次分配完成，已分配 {} 人，待调剂 {} 人",
                cycleId, result.getAssignedCount(), result.getUnassignedCount());
        return result;
    }

    @Override
    public List<SessionAssignmentResultDTO.UnassignedItem> listUnassigned(Integer cycleId) {
        validateCycleExists(cycleId);
        Map<Integer, Resume> resumeById = loadSubmittedResumes(cycleId);
        Set<Integer> alreadyScheduled = loadActivelyScheduledResumeIds(cycleId);
        Map<Integer, String> deptNames = loadAllDeptNames();

        List<InterviewPreference> preferences = interviewPreferenceService.list(
                new LambdaQueryWrapper<InterviewPreference>().eq(InterviewPreference::getCycleId, cycleId));

        return preferences.stream()
                .filter(p -> resumeById.containsKey(p.getResumeId()))
                .filter(p -> !alreadyScheduled.contains(p.getResumeId()))
                .map(p -> buildUnassigned(p, resumeById.get(p.getResumeId()), deptNames, "待人工调剂"))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SessionAssignmentResultDTO.AssignedItem manualAssign(Integer resumeId, Integer targetSessionId) {
        InterviewSession target = interviewSessionService.getById(targetSessionId);
        if (target == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_NOT_FOUND);
        }
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }

        // 先占用目标场次（原子），失败说明已满
        if (interviewSessionMapper.occupyOneIfAvailable(targetSessionId) != 1) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_FULL);
        }

        // 已有有效安排则释放原场次
        InterviewSchedule schedule = interviewScheduleService.getOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resumeId)
                        .eq(InterviewSchedule::getCycleId, target.getCycleId())
                        .eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE)
                        .last("LIMIT 1"), false);
        if (schedule != null && schedule.getSessionId() != null
                && !schedule.getSessionId().equals(targetSessionId)) {
            interviewSessionMapper.releaseOne(schedule.getSessionId());
        }

        // 重新读取目标场次占用数，本人索引 = 占用数 - 1
        InterviewSession refreshed = interviewSessionService.getById(targetSessionId);
        int index = Math.max(0, (refreshed.getCurrentOccupied() == null ? 1 : refreshed.getCurrentOccupied()) - 1);
        InterviewTimeSlot timeSlot = interviewTimeSlotService.getById(target.getTimeSlotId());
        LocalDateTime start = computeStart(timeSlot, index, durationOf(target));

        boolean isNew = schedule == null;
        if (isNew) {
            schedule = new InterviewSchedule()
                    .setResumeId(resumeId)
                    .setCycleId(target.getCycleId())
                    .setSyncStatus(0)
                    .setNotifStatus(0);
        }
        schedule.setSlotId(null)
                .setSessionId(targetSessionId)
                .setDeptId(target.getDeptId())
                .setInterviewTime(start)
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes("人工调剂 - " + target.getLocation());
        if (isNew) {
            interviewScheduleService.save(schedule);
        } else {
            interviewScheduleService.updateById(schedule);
        }

        SessionState state = new SessionState(target, timeSlot);
        Map<Integer, String> deptNames = loadAllDeptNames();
        SessionAssignmentResultDTO.AssignedItem item = buildAssigned(schedule, resume, state, 0, deptNames);
        log.info("人工调剂完成，resumeId={}, targetSessionId={}, scheduleId={}",
                resumeId, targetSessionId, schedule.getScheduleId());
        return item;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 从候选场次中挑选：时间窗被候选人接受且仍有剩余名额，取剩余名额最多者（负载均衡），
     * 再按日期/开始时间/场次ID稳定排序。
     */
    private SessionState pickSession(List<SessionState> states, List<Integer> acceptableTimeSlotIds) {
        if (states == null || states.isEmpty()) {
            return null;
        }
        return states.stream()
                .filter(s -> s.remaining() > 0)
                .filter(s -> acceptableTimeSlotIds.contains(s.session.getTimeSlotId()))
                .max(Comparator
                        .comparingInt(SessionState::remaining)
                        .thenComparing(Comparator.comparing(SessionState::sortKey).reversed()))
                .orElse(null);
    }

    private InterviewSchedule persistSchedule(Resume resume, SessionState state, int index, int matchedChoice) {
        LocalDateTime start = computeStart(state.timeSlot, index, durationOf(state.session));
        InterviewSchedule schedule = new InterviewSchedule()
                .setResumeId(resume.getResumeId())
                .setCycleId(state.session.getCycleId())
                .setSlotId(null)
                .setSessionId(state.session.getSessionId())
                .setDeptId(state.session.getDeptId())
                .setInterviewTime(start)
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes("自动分配 - 第" + (matchedChoice == 0 ? "" : matchedChoice) + "志愿 - " + state.session.getLocation())
                .setSyncStatus(0)
                .setNotifStatus(0);
        interviewScheduleService.save(schedule);
        return schedule;
    }

    private void persistOccupancy(List<SessionState> touchedStates) {
        for (SessionState state : touchedStates) {
            InterviewSession update = new InterviewSession()
                    .setSessionId(state.session.getSessionId())
                    .setCurrentOccupied(state.occupied);
            interviewSessionService.updateById(update);
        }
    }

    private LocalDateTime computeStart(InterviewTimeSlot timeSlot, int index, int durationMinutes) {
        if (timeSlot == null || timeSlot.getInterviewDate() == null || timeSlot.getStartTime() == null) {
            return null;
        }
        return LocalDateTime.of(timeSlot.getInterviewDate(), timeSlot.getStartTime())
                .plusMinutes((long) index * durationMinutes);
    }

    private int durationOf(InterviewSession session) {
        return session.getInterviewDurationMinutes() == null
                ? DEFAULT_DURATION_MINUTES : session.getInterviewDurationMinutes();
    }

    private Map<Integer, Resume> loadSubmittedResumes(Integer cycleId) {
        return resumeService.getAllResumesByCycleId(cycleId).stream()
                .filter(r -> r.getStatus() != null && r.getStatus() >= RESUME_STATUS_SUBMITTED)
                .collect(Collectors.toMap(Resume::getResumeId, r -> r, (a, b) -> a));
    }

    private Set<Integer> loadActivelyScheduledResumeIds(Integer cycleId) {
        return interviewScheduleService.list(new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE))
                .stream().map(InterviewSchedule::getResumeId).collect(Collectors.toSet());
    }

    private Map<Integer, List<Integer>> loadAcceptedTimeSlotIds(List<Integer> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return new HashMap<>();
        }
        List<InterviewPreferenceTime> rows = preferenceTimeMapper.selectList(
                new LambdaQueryWrapper<InterviewPreferenceTime>()
                        .in(InterviewPreferenceTime::getResumeId, resumeIds));
        return rows.stream().collect(Collectors.groupingBy(
                InterviewPreferenceTime::getResumeId,
                Collectors.mapping(InterviewPreferenceTime::getTimeSlotId, Collectors.toList())));
    }

    private Map<Integer, List<SessionState>> loadSessionStates(Integer cycleId) {
        List<InterviewSession> sessions = interviewSessionService.list(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getCycleId, cycleId)
                        .eq(InterviewSession::getStatus, SESSION_STATUS_AVAILABLE));
        if (sessions.isEmpty()) {
            return new HashMap<>();
        }
        List<Integer> timeSlotIds = sessions.stream()
                .map(InterviewSession::getTimeSlotId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, InterviewTimeSlot> timeSlotMap = interviewTimeSlotService.listByIds(timeSlotIds).stream()
                .collect(Collectors.toMap(InterviewTimeSlot::getTimeSlotId, ts -> ts, (a, b) -> a));

        Map<Integer, List<SessionState>> byDept = new HashMap<>();
        for (InterviewSession session : sessions) {
            SessionState state = new SessionState(session, timeSlotMap.get(session.getTimeSlotId()));
            byDept.computeIfAbsent(session.getDeptId(), k -> new ArrayList<>()).add(state);
        }
        return byDept;
    }

    private Map<Integer, String> loadAllDeptNames() {
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName, (a, b) -> a));
    }

    private SessionAssignmentResultDTO.AssignedItem buildAssigned(InterviewSchedule schedule, Resume resume,
                                                                  SessionState state, int matchedChoice,
                                                                  Map<Integer, String> deptNames) {
        SessionAssignmentResultDTO.AssignedItem item = new SessionAssignmentResultDTO.AssignedItem();
        item.setScheduleId(schedule.getScheduleId());
        item.setResumeId(resume.getResumeId());
        item.setUserId(resume.getUserId());
        item.setName(resumeDataService.getResumeName(resume));
        item.setMatchedChoice(matchedChoice == 0 ? null : matchedChoice);
        item.setSessionId(state.session.getSessionId());
        item.setDeptId(state.session.getDeptId());
        item.setDeptName(deptNames.get(state.session.getDeptId()));
        item.setLocation(state.session.getLocation());
        item.setInterviewStartTime(schedule.getInterviewTime());
        if (schedule.getInterviewTime() != null) {
            item.setInterviewEndTime(schedule.getInterviewTime().plusMinutes(durationOf(state.session)));
        }
        return item;
    }

    private SessionAssignmentResultDTO.UnassignedItem buildUnassigned(InterviewPreference pref, Resume resume,
                                                                      Map<Integer, String> deptNames, String reason) {
        SessionAssignmentResultDTO.UnassignedItem item = new SessionAssignmentResultDTO.UnassignedItem();
        item.setResumeId(pref.getResumeId());
        if (resume != null) {
            item.setUserId(resume.getUserId());
            item.setName(resumeDataService.getResumeName(resume));
        }
        item.setFirstDeptId(pref.getFirstDeptId());
        item.setFirstDeptName(pref.getFirstDeptId() == null ? null : deptNames.get(pref.getFirstDeptId()));
        item.setSecondDeptId(pref.getSecondDeptId());
        item.setSecondDeptName(pref.getSecondDeptId() == null ? null : deptNames.get(pref.getSecondDeptId()));
        item.setReason(reason);
        return item;
    }

    private void validateCycleExists(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }

    /**
     * 分配过程中的场次状态：追踪本轮已占用人数以细分时间。
     */
    private static final class SessionState {
        private final InterviewSession session;
        private final InterviewTimeSlot timeSlot;
        private int occupied;

        private SessionState(InterviewSession session, InterviewTimeSlot timeSlot) {
            this.session = session;
            this.timeSlot = timeSlot;
            this.occupied = session.getCurrentOccupied() == null ? 0 : session.getCurrentOccupied();
        }

        private int remaining() {
            int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
            return Math.max(0, capacity - occupied);
        }

        /** 占用一个名额并返回本人在该场次内的次序索引（0-based）。 */
        private int occupyNext() {
            return occupied++;
        }

        /** 稳定排序键：日期+开始时间+场次ID。 */
        private String sortKey() {
            String date = timeSlot != null && timeSlot.getInterviewDate() != null
                    ? timeSlot.getInterviewDate().toString() : "9999-12-31";
            String time = timeSlot != null && timeSlot.getStartTime() != null
                    ? timeSlot.getStartTime().toString() : "23:59";
            return date + "T" + time + "#" + String.format("%08d", session.getSessionId());
        }
    }
}
