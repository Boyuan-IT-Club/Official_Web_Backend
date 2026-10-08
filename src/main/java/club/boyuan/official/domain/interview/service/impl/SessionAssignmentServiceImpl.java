package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeResponseDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.interview.service.ISessionAssignmentService;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.resume.service.ResumeDataService;
import club.boyuan.official.infra.notification.InterviewNotificationType;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewNotificationLog;
import club.boyuan.official.persistence.entity.InterviewSessionDept;
import club.boyuan.official.persistence.entity.InterviewPreference;
import club.boyuan.official.persistence.entity.InterviewPreferenceTime;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceTimeMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    /** 初筛未通过：不参与分配 */
    private static final int RESUME_STATUS_SCREEN_REJECTED = 5;
    private static final int SESSION_STATUS_AVAILABLE = 1;
    private static final int SCHEDULE_STATUS_ACTIVE = 1;
    private static final int DEFAULT_DURATION_MINUTES = 10;

    static final String REASON_NO_TIME_SLOT = "未勾选可接受的时间窗";
    static final String REASON_NO_MATCHING_SESSION = "志愿部门的可选场次已满或无匹配时段";
    static final String REASON_NOT_YET_ASSIGNED = "有可用场次，待执行一键分配";

    private final IRecruitmentCycleService recruitmentCycleService;
    private final IInterviewPreferenceService interviewPreferenceService;
    private final InterviewPreferenceTimeMapper preferenceTimeMapper;
    private final IInterviewSessionService interviewSessionService;
    private final InterviewSessionMapper interviewSessionMapper;
    private final club.boyuan.official.persistence.mapper.InterviewSessionDeptMapper interviewSessionDeptMapper;
    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final IResumeService resumeService;
    private final ResumeDataService resumeDataService;
    private final DepartmentMapper departmentMapper;
    private final InterviewNotificationLogMapper notificationLogMapper;

    @Override
    @Transactional
    public SessionAssignmentResultDTO assign(Integer cycleId) {
        validateCycleExists(cycleId);
        log.info("开始为周期 {} 执行场次分配", cycleId);

        Map<Integer, Resume> resumeById = loadSubmittedResumes(cycleId);
        List<InterviewSchedule> cycleRows = loadCycleScheduleRows(cycleId);
        Set<Integer> alreadyScheduled = activeResumeIds(cycleRows);
        // 被取消过的人：重排时复用他那一行，见 persistSchedule
        Map<Integer, InterviewSchedule> inactiveRows = cycleRows.stream()
                .filter(s -> !Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(s.getStatus()))
                .collect(Collectors.toMap(InterviewSchedule::getResumeId, s -> s, (a, b) -> a));

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
                result.getUnassigned().add(buildUnassigned(pref, resume, deptNames, REASON_NO_TIME_SLOT));
                continue;
            }

            SessionState chosen = null;
            int matchedChoice = 0;
            Integer matchedDeptId = null;
            if (pref.getFirstDeptId() != null) {
                chosen = pickSession(statesByDept.get(pref.getFirstDeptId()), acceptable);
                if (chosen != null) {
                    matchedChoice = 1;
                    matchedDeptId = pref.getFirstDeptId();
                }
            }
            if (chosen == null && pref.getSecondDeptId() != null) {
                chosen = pickSession(statesByDept.get(pref.getSecondDeptId()), acceptable);
                if (chosen != null) {
                    matchedChoice = 2;
                    matchedDeptId = pref.getSecondDeptId();
                }
            }

            if (chosen == null) {
                result.getUnassigned().add(buildUnassigned(pref, resume, deptNames, REASON_NO_MATCHING_SESSION));
                continue;
            }

            int index = chosen.occupyNext();
            if (!touchedStates.contains(chosen)) {
                touchedStates.add(chosen);
            }
            InterviewSchedule schedule = persistSchedule(resume, chosen, index, matchedChoice, matchedDeptId,
                    inactiveRows.get(resume.getResumeId()));
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
        Set<Integer> alreadyScheduled = activeResumeIds(loadCycleScheduleRows(cycleId));
        Map<Integer, String> deptNames = loadAllDeptNames();

        List<InterviewPreference> pending = interviewPreferenceService.list(
                        new LambdaQueryWrapper<InterviewPreference>().eq(InterviewPreference::getCycleId, cycleId))
                .stream()
                .filter(p -> resumeById.containsKey(p.getResumeId()))
                .filter(p -> !alreadyScheduled.contains(p.getResumeId()))
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            return List.of();
        }

        // 原因按当前数据即时推断（与 assign 用同一套判定），而不是写死一句「待人工调剂」：
        // 管理员看名单时要知道是学生没勾时间、还是场次不够、还是只差点一下分配。
        Map<Integer, List<Integer>> acceptedTimeSlotIds = loadAcceptedTimeSlotIds(
                pending.stream().map(InterviewPreference::getResumeId).collect(Collectors.toList()));
        Map<Integer, List<SessionState>> statesByDept = loadSessionStates(cycleId);

        return pending.stream()
                .map(p -> buildUnassigned(p, resumeById.get(p.getResumeId()), deptNames,
                        explainUnassigned(p, acceptedTimeSlotIds.getOrDefault(p.getResumeId(), List.of()), statesByDept)))
                .collect(Collectors.toList());
    }

    /**
     * 用与 {@link #assign} 相同的判定解释一名候选人此刻为什么还没被排上。
     * 剩余名额按库里当前占用计算，所以「有可用场次」意味着现在点一键分配就能排进去。
     */
    private String explainUnassigned(InterviewPreference pref, List<Integer> acceptable,
                                     Map<Integer, List<SessionState>> statesByDept) {
        if (acceptable.isEmpty()) {
            return REASON_NO_TIME_SLOT;
        }
        boolean firstOk = pref.getFirstDeptId() != null
                && pickSession(statesByDept.get(pref.getFirstDeptId()), acceptable) != null;
        boolean secondOk = pref.getSecondDeptId() != null
                && pickSession(statesByDept.get(pref.getSecondDeptId()), acceptable) != null;
        return firstOk || secondOk ? REASON_NOT_YET_ASSIGNED : REASON_NO_MATCHING_SESSION;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionAssignmentResultDTO.AssignedItem assignOnline(Integer resumeId, LocalDateTime interviewTime) {
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }

        // 和 manualAssign 一样按任意状态找本届那一行：只找有效的会漏掉「已取消」，
        // 接着 insert 就撞 uk_resume_cycle
        InterviewSchedule schedule = interviewScheduleService.getOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resumeId)
                        .eq(InterviewSchedule::getCycleId, resume.getCycleId())
                        .last("LIMIT 1"), false);

        /*
         * 时间：调用方给了就用给的，没给就沿用这条安排上已有的。
         * 两者都没有说明这人从没被排过、管理员也没指定——这种情况不能瞎编一个
         * 时间发出去，直接要求补齐。
         */
        LocalDateTime finalTime = interviewTime != null
                ? interviewTime
                : (schedule == null ? null : schedule.getInterviewTime());
        if (finalTime == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD,
                    "请指定线上面试时间");
        }

        boolean isNew = schedule == null;
        if (isNew) {
            schedule = new InterviewSchedule()
                    .setResumeId(resumeId)
                    .setUserId(resume.getUserId())
                    .setCycleId(resume.getCycleId())
                    .setSyncStatus(0);
        } else if (Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(schedule.getStatus())
                && schedule.getSessionId() != null) {
            // 本来在某个教室里，转线上就该把那个座位让出来——否则线下场次白占一格。
            // 已取消的那行在取消时已经还过名额了，不能再还一次
            interviewSessionMapper.releaseOne(schedule.getSessionId());
        }

        Integer releasedFrom = schedule.getSessionId();
        schedule.setSlotId(null)
                .setSessionId(null)          // 线上不占场次，地点是周期级的会议链接
                .setInterviewMode(1)
                .setInterviewTime(finalTime)
                .setTimeOverridden(1)        // 时间是人工指定的，不该被公式重算覆盖
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setSyncStatus(0)
                .setNotifStatus(0)           // 进待补发：学生得知道自己改成线上了
                .setNotes("线上面试 - 管理员安排");
        if (isNew) {
            interviewScheduleService.save(schedule);
        } else {
            interviewScheduleService.updateById(schedule);
        }
        // 时间和参加方式都变了，旧通知不该再占去重名额
        detachScheduleNotices(schedule.getScheduleId());

        log.info("已安排线上面试，resumeId={}, scheduleId={}, time={}, 释放场次={}",
                resumeId, schedule.getScheduleId(), finalTime, releasedFrom);

        SessionAssignmentResultDTO.AssignedItem item = new SessionAssignmentResultDTO.AssignedItem();
        item.setResumeId(resumeId);
        item.setScheduleId(schedule.getScheduleId());
        item.setUserId(resume.getUserId());
        item.setName(resumeDataService.getResumeName(resume));
        item.setInterviewStartTime(finalTime);
        item.setLocation("线上面试");
        return item;
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

        // 按任意状态找这人本届的那一行（uk_resume_cycle：一人一届只有一行）。
        // 只找有效的会漏掉「已取消」的行，接着 insert 就撞唯一键。
        InterviewSchedule schedule = interviewScheduleService.getOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resumeId)
                        .eq(InterviewSchedule::getCycleId, target.getCycleId())
                        .last("LIMIT 1"), false);
        boolean wasActive = schedule != null
                && Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(schedule.getStatus());
        // 有效安排换场要归还原场次；已取消的那行在取消时已经还过名额了
        if (wasActive && schedule.getSessionId() != null
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
                    .setUserId(resume.getUserId())
                    .setCycleId(target.getCycleId())
                    .setSyncStatus(0)
                    .setNotifStatus(0);
        }
        schedule.setSlotId(null)
                .setSessionId(targetSessionId)
                .setDeptId(target.getDeptId())
                .setInterviewTime(start)
                // 换场已经改变了时间窗/日期，旧的人工指定时间语义错误，故重置回公式生成
                .setTimeOverridden(0)
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                // 排进实体场次 = 回到线下。不清这个标记的话，一个转过线上的人
                // 再被调剂回教室，学生端还会继续显示会议链接
                .setInterviewMode(0)
                // 时间和场次都变了，之前那封通知描述的是一个不存在的安排 ——
                // 这个人重新算作「未通知」，否则通知中心按 notif_status 统计时
                // 会把他归进「已发」，管理员在界面上根本看不到这个待办。
                // 2026-10-07 线上五位改期重排的同学就是这样：邮箱里拿着旧时间，
                // 通知中心却显示已通知，没人发现。
                .setNotifStatus(0)
                .setNotes("人工调剂 - " + target.getLocation());
        if (isNew) {
            interviewScheduleService.save(schedule);
        } else {
            interviewScheduleService.updateById(schedule);
            // 不再只在「从已取消复活」时摘：只要安排内容变了，旧通知就不该
            // 再占去重名额，否则补发会被 alreadySent 静默跳过
            detachScheduleNotices(schedule.getScheduleId());
        }

        SessionState state = new SessionState(target, timeSlot);
        Map<Integer, String> deptNames = loadAllDeptNames();
        SessionAssignmentResultDTO.AssignedItem item = buildAssigned(schedule, resume, state, 0, deptNames);
        log.info("人工调剂完成，resumeId={}, targetSessionId={}, scheduleId={}",
                resumeId, targetSessionId, schedule.getScheduleId());
        return item;
    }

    @Override
    @Transactional
    public UpdateInterviewTimeResponseDTO updateInterviewTime(Integer scheduleId, LocalDateTime interviewTime) {
        return updateInterviewTime(scheduleId, interviewTime, null);
    }

    @Override
    @Transactional
    public UpdateInterviewTimeResponseDTO updateInterviewTime(
            Integer scheduleId, LocalDateTime interviewTime, Integer targetSessionId) {
        if (scheduleId == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SCHEDULE_NOT_FOUND);
        }
        if (interviewTime == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "面试时间不能为空");
        }

        InterviewSchedule schedule = interviewScheduleService.getById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SCHEDULE_NOT_FOUND);
        }
        if (!Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(schedule.getStatus())) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SCHEDULE_NOT_ACTIVE);
        }

        List<String> warnings = new ArrayList<>();

        /*
         * 换场（可选）。方案B 下面试房间属于场次，安排本身不存地点——管理员要改地点
         * 就是把人挪到另一个场次，所以和改时间放在同一个入口里一起做。
         *
         * 名额按原子占用/归还走，与人工调剂一致；目标场次满了直接拒绝，
         * 免得把一个场次塞爆。目标场次就是原场次时什么都不做。
         */
        Integer originalSessionId = schedule.getSessionId();
        boolean movedSession = targetSessionId != null && !targetSessionId.equals(originalSessionId);
        if (movedSession) {
            InterviewSession target = interviewSessionService.getById(targetSessionId);
            if (target == null) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_NOT_FOUND);
            }
            if (!Objects.equals(schedule.getCycleId(), target.getCycleId())) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_CYCLE_MISMATCH);
            }
            /*
             * 手动换场不按容量拦：管理员是明确要把这个人放进这一场（临时加座、
             * 背靠背面试），容量满了就拒绝反而挡了正事。超额只给可读告警。
             * 一键分配与人工调剂仍走 occupyOneIfAvailable，不受此影响。
             */
            int occupied = target.getCurrentOccupied() == null ? 0 : target.getCurrentOccupied();
            Integer capacity = target.getCapacity();
            if (capacity != null && occupied >= capacity) {
                warnings.add("场次 #" + targetSessionId + " 已满（" + occupied + "/" + capacity
                        + "），本次为超额安排");
            }
            interviewSessionMapper.occupyOneIgnoringCapacity(targetSessionId);
            if (originalSessionId != null) {
                interviewSessionMapper.releaseOne(originalSessionId);
            }
            schedule.setSessionId(targetSessionId);
        }

        // 场次 / 时间窗存在性与跨周期一致性：被删或错配 → 明确业务异常
        InterviewTimeSlot timeSlot = null;
        if (schedule.getSessionId() != null) {
            InterviewSession session = interviewSessionService.getById(schedule.getSessionId());
            if (session == null) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_NOT_FOUND);
            }
            if (!Objects.equals(schedule.getCycleId(), session.getCycleId())) {
                throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_CYCLE_MISMATCH);
            }
            if (session.getTimeSlotId() != null) {
                timeSlot = interviewTimeSlotService.getById(session.getTimeSlotId());
                if (timeSlot == null) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_NOT_FOUND);
                }
                if (!Objects.equals(schedule.getCycleId(), timeSlot.getCycleId())) {
                    throw new BusinessException(BusinessExceptionEnum.INTERVIEW_TIME_SLOT_CYCLE_MISMATCH);
                }
                // 越界只告警不拒绝：核心诉求就是允许指定时间窗之外的任意钟点
                if (timeSlot.getInterviewDate() != null
                        && !timeSlot.getInterviewDate().equals(interviewTime.toLocalDate())) {
                    warnings.add("指定时间 " + formatTime(interviewTime)
                            + " 不在场次时间窗日期 " + timeSlot.getInterviewDate() + " 内");
                } else if (timeSlot.getStartTime() != null && timeSlot.getEndTime() != null
                        && (interviewTime.toLocalTime().isBefore(timeSlot.getStartTime())
                        || interviewTime.toLocalTime().isAfter(timeSlot.getEndTime()))) {
                    warnings.add("指定时间 " + formatTime(interviewTime) + " 超出该场次时间窗 "
                            + timeSlot.getStartTime() + "-" + timeSlot.getEndTime());
                }
            }
        }

        // 同场次同时刻冲突：只给可读告警，不拒绝（管理员可能安排背靠背/双人同场）
        if (schedule.getSessionId() != null) {
            long conflict = interviewScheduleService.count(new LambdaQueryWrapper<InterviewSchedule>()
                    .eq(InterviewSchedule::getSessionId, schedule.getSessionId())
                    .eq(InterviewSchedule::getInterviewTime, interviewTime)
                    .eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE)
                    .ne(InterviewSchedule::getScheduleId, scheduleId));
            if (conflict > 0) {
                warnings.add("该场次在 " + formatTime(interviewTime) + " 已有其他候选人，请注意时间冲突");
            }
        }

        // 只更新非空字段：interview_time / time_overridden / sync_status / notif_status。
        // 保留 feishu_record_id，飞书同步按 sync_status=0 拉到后走 batch_update 更新已有行。
        InterviewSchedule update = new InterviewSchedule()
                .setScheduleId(scheduleId)
                .setInterviewTime(interviewTime)
                .setTimeOverridden(1)
                .setSyncStatus(0)
                .setNotifStatus(0);
        if (movedSession) {
            update.setSessionId(targetSessionId);
        }
        interviewScheduleService.updateById(update);
        /*
         * 上面已经把 notif_status 置 0（界面显示「待发」），但旧通知还挂在这条
         * 安排上，补发时会被 sendScheduleNotices 的 alreadySent 滤掉 ——
         * 管理员看到「待发 1」，点了发送却返回 skipped，什么都没发出去。
         * 两个口径必须一起动。
         */
        detachScheduleNotices(scheduleId);

        UpdateInterviewTimeResponseDTO response = new UpdateInterviewTimeResponseDTO();
        response.setScheduleId(scheduleId);
        response.setInterviewTime(interviewTime);
        response.setSessionId(schedule.getSessionId());
        if (schedule.getSessionId() != null) {
            InterviewSession current = interviewSessionService.getById(schedule.getSessionId());
            response.setLocation(current == null ? null : current.getLocation());
        }
        response.setTimeOverridden(1);
        response.setSyncStatus(0);
        response.setNotifStatus(0);
        if (!warnings.isEmpty()) {
            response.setWarning(String.join("；", warnings));
            log.warn("手动调整面试时间 scheduleId={}, interviewTime={}, warnings={}",
                    scheduleId, interviewTime, warnings);
        } else {
            log.info("手动调整面试时间 scheduleId={}, interviewTime={}, sessionId={}->{}",
                    scheduleId, interviewTime, originalSessionId, schedule.getSessionId());
        }
        return response;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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

    /**
     * @param matchedDeptId 学生被匹配上的部门。
     *
     * 多部门场次下不能再写 session.getDeptId()：一场同时面三个部门时，
     * 那个值只是「主部门」，照抄会把技术部的同学记成媒体部的面试，
     * 后续录取部门、通知邮件全跟着错。要记的是他因为哪个志愿被排进来的。
     *
     * @param reusable 这人本届被取消过的那一行；没有则为 null。
     *                 (resume_id, cycle_id) 上有唯一键 uk_resume_cycle，而取消只把行
     *                 置为已取消、不删除——重排时再 insert 一行就撞唯一键，整次一键分配
     *                 跟着回滚。所以有旧行就原地复用。
     */
    private InterviewSchedule persistSchedule(Resume resume, SessionState state, int index,
                                              int matchedChoice, Integer matchedDeptId,
                                              InterviewSchedule reusable) {
        LocalDateTime start = computeStart(state.timeSlot, index, durationOf(state.session));
        InterviewSchedule schedule = reusable != null ? reusable : new InterviewSchedule();
        schedule.setResumeId(resume.getResumeId())
                .setUserId(resume.getUserId())
                .setCycleId(state.session.getCycleId())
                .setSlotId(null)
                .setSessionId(state.session.getSessionId())
                .setDeptId(matchedDeptId != null ? matchedDeptId : state.session.getDeptId())
                .setInterviewTime(start)
                // 旧行上可能留着取消前的「手调」标记；新安排的时间是公式生成的
                .setTimeOverridden(0)
                // 一键分配排的都是实体场次，顺带把线上标记清掉
                .setInterviewMode(0)
                .setStatus(SCHEDULE_STATUS_ACTIVE)
                .setNotes("自动分配 - 第" + (matchedChoice == 0 ? "" : matchedChoice) + "志愿 - " + state.session.getLocation())
                .setSyncStatus(0)
                .setNotifStatus(0);
        if (reusable == null) {
            interviewScheduleService.save(schedule);
        } else {
            interviewScheduleService.updateById(schedule);
            detachScheduleNotices(schedule.getScheduleId());
        }
        return schedule;
    }

    /**
     * 已取消的安排被重新启用，等于一份新安排——时间、地点大概率都变了。
     *
     * 但场次类通知按 (notification_type, schedule_id) 去重（uk_type_schedule），
     * 旧安排发过的「面试安排通知 / 前一天 / 当天提醒」会让新安排的同类通知被当成
     * 已发而跳过：学生手里拿着旧时间，系统却以为通知过了。
     *
     * 把这些记录从这条安排上摘下（schedule_id 置空）。发送历史（收件人、时间）
     * 原样保留，只是不再占新安排的去重名额。
     */
    private void detachScheduleNotices(Integer scheduleId) {
        if (scheduleId == null) {
            return;
        }
        notificationLogMapper.update(null, new LambdaUpdateWrapper<InterviewNotificationLog>()
                .set(InterviewNotificationLog::getScheduleId, null)
                .eq(InterviewNotificationLog::getScheduleId, scheduleId)
                .in(InterviewNotificationLog::getNotificationType, List.of(
                        InterviewNotificationType.BOOKING_SUCCESS.name(),
                        InterviewNotificationType.EVE_REMINDER.name(),
                        InterviewNotificationType.DAY_REMINDER.name())));
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

    /**
     * 参与分配的简历：已提交及以后，但<b>排除初筛未通过</b>(5)。
     * 未通过初筛的同学本届流程已结束，再把他排进场次会让面试官白等一个不会来的人。
     */
    private Map<Integer, Resume> loadSubmittedResumes(Integer cycleId) {
        return resumeService.getAllResumesByCycleId(cycleId).stream()
                .filter(r -> r.getStatus() != null
                        && r.getStatus() >= RESUME_STATUS_SUBMITTED
                        && r.getStatus() != RESUME_STATUS_SCREEN_REJECTED)
                .collect(Collectors.toMap(Resume::getResumeId, r -> r, (a, b) -> a));
    }

    /** 本届所有安排行，任意状态（含已取消）。uk_resume_cycle 保证一人一行 */
    private List<InterviewSchedule> loadCycleScheduleRows(Integer cycleId) {
        return interviewScheduleService.list(new LambdaQueryWrapper<InterviewSchedule>()
                .eq(InterviewSchedule::getCycleId, cycleId));
    }

    private static Set<Integer> activeResumeIds(List<InterviewSchedule> rows) {
        return rows.stream()
                .filter(s -> Integer.valueOf(SCHEDULE_STATUS_ACTIVE).equals(s.getStatus()))
                .map(InterviewSchedule::getResumeId)
                .collect(Collectors.toSet());
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

        // 一个场次可以同时面多个部门（V36），所以它要挂到它服务的**每个**部门下。
        // 注意 SessionState 只建一个、被多个部门共享 —— 容量是场次的，不是部门的：
        // 每个部门各建一份 state 会让同一场次的容量被重复计算好几遍，超额分配。
        Map<Integer, List<Integer>> deptsBySession = loadDeptsBySession(
                sessions.stream().map(InterviewSession::getSessionId).filter(Objects::nonNull).toList());

        Map<Integer, List<SessionState>> byDept = new HashMap<>();
        for (InterviewSession session : sessions) {
            SessionState state = new SessionState(session, timeSlotMap.get(session.getTimeSlotId()));
            List<Integer> depts = deptsBySession.get(session.getSessionId());
            if (depts == null || depts.isEmpty()) {
                // 关联表没有记录时回落到场次自己的 dept_id。
                // 正常不该发生（V36 已回填），但缺了它，老数据会直接分不出去
                depts = session.getDeptId() == null ? List.of() : List.of(session.getDeptId());
            }
            for (Integer deptId : depts) {
                byDept.computeIfAbsent(deptId, k -> new ArrayList<>()).add(state);
            }
        }
        return byDept;
    }

    /**
     * 一次把这批场次覆盖的部门全查出来，避免在循环里逐场次查（N+1）。
     */
    private Map<Integer, List<Integer>> loadDeptsBySession(List<Integer> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new HashMap<>();
        }
        List<InterviewSessionDept> rows = interviewSessionDeptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewSessionDept>()
                        .in(InterviewSessionDept::getSessionId, sessionIds));
        Map<Integer, List<Integer>> out = new HashMap<>();
        for (InterviewSessionDept r : rows) {
            out.computeIfAbsent(r.getSessionId(), k -> new ArrayList<>()).add(r.getDeptId());
        }
        return out;
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
        // 展示的是「他被排进哪个部门的面试」，多部门场次下不等于场次的主部门
        Integer shownDept = schedule.getDeptId() != null ? schedule.getDeptId() : state.session.getDeptId();
        item.setDeptId(shownDept);
        item.setDeptName(deptNames.get(shownDept));
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
