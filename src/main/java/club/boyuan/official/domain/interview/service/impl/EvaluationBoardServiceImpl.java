package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.EvaluationBoardDTO;
import club.boyuan.official.domain.interview.dto.EvaluationBoardSeedDTO;
import club.boyuan.official.domain.interview.service.IEvaluationBoardService;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.persistence.entity.CollabDoc;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.EvaluationDimension;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.CollabDocMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.EvaluationDimensionMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 协同评价表生命周期实现。
 *
 * @author dhy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationBoardServiceImpl implements IEvaluationBoardService {

    /** 协同文档名前缀，与前端 provider 和协同服务约定一致 */
    private static final String DOC_NAME_PREFIX = "eval-board:";

    /** 面试安排状态：已安排 */
    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final CollabDocMapper collabDocMapper;
    private final EvaluationDimensionMapper evaluationDimensionMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    private final SessionInterviewerMapper sessionInterviewerMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ResumeMapper resumeMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final IResumeService resumeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationBoardDTO openBoard(Integer cycleId) {
        requireCycle(cycleId);

        List<InterviewSchedule> roster = listRoster(cycleId);
        if (roster.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_NO_ROSTER);
        }

        // 该周期一列维度都没有时补上默认四项，否则开出来的表没有可评分的列
        List<EvaluationDimension> dimensions = listDimensions(cycleId);
        if (dimensions.isEmpty()) {
            for (EvaluationDimension dimension : EvaluationDimensionServiceImpl.defaultDimensions(cycleId)) {
                evaluationDimensionMapper.insert(dimension);
            }
            dimensions = listDimensions(cycleId);
            log.info("周期 {} 无评分维度，已播种默认维度 {} 项", cycleId, dimensions.size());
        }

        String docName = docName(cycleId);
        CollabDoc doc = collabDocMapper.selectById(docName);
        if (doc == null) {
            doc = new CollabDoc()
                    .setDocName(docName)
                    .setCycleId(cycleId)
                    .setLocked(0);
            collabDocMapper.insert(doc);
            log.info("周期 {} 开启协同评价表，文档 {}，候选人 {} 人", cycleId, docName, roster.size());
        }

        return toBoardDTO(doc, roster.size(), dimensions.size());
    }

    @Override
    public EvaluationBoardDTO getBoard(Integer cycleId) {
        CollabDoc doc = requireDoc(cycleId);
        return toBoardDTO(doc, listRoster(cycleId).size(), listDimensions(cycleId).size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationBoardDTO setLocked(Integer cycleId, boolean locked) {
        CollabDoc doc = requireDoc(cycleId);
        doc.setLocked(locked ? 1 : 0);
        collabDocMapper.updateById(doc);
        log.info("周期 {} 评价表{}", cycleId, locked ? "已锁定" : "已解锁");
        return toBoardDTO(doc, listRoster(cycleId).size(), listDimensions(cycleId).size());
    }

    @Override
    public EvaluationBoardSeedDTO getSeed(Integer cycleId) {
        CollabDoc doc = requireDoc(cycleId);

        EvaluationBoardSeedDTO seed = new EvaluationBoardSeedDTO();
        seed.setDocName(doc.getDocName());
        seed.setCycleId(cycleId);
        seed.setLocked(Integer.valueOf(1).equals(doc.getLocked()));
        seed.setColumns(listDimensions(cycleId).stream()
                .map(EvaluationDimensionServiceImpl::toDTO)
                .collect(Collectors.toList()));
        seed.setRows(buildRows(cycleId));
        seed.setInterviewerNames(resolveInterviewerNames(seed.getRows()));
        return seed;
    }

    private Map<Integer, String> resolveInterviewerNames(List<EvaluationBoardSeedDTO.RowSeed> rows) {
        Set<Integer> interviewerIds = rows.stream()
                .flatMap(row -> row.getInterviewerUserIds().stream())
                .collect(Collectors.toSet());
        if (interviewerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> names = new LinkedHashMap<>();
        for (User user : selectUsersByIds(interviewerIds)) {
            names.put(user.getUserId(), user.getName() != null ? user.getName() : user.getUsername());
        }
        return names;
    }

    @Override
    public ResumeDTO getCandidateResume(Integer cycleId, Integer scheduleId, Integer viewerUserId, boolean admin) {
        InterviewSchedule schedule = interviewScheduleMapper.selectById(scheduleId);
        if (schedule == null || !Objects.equals(schedule.getCycleId(), cycleId)) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SCHEDULE_NOT_FOUND);
        }

        if (!admin && !isInterviewerOf(schedule.getSessionId(), viewerUserId)) {
            log.warn("用户 {} 试图查看非本人负责场次的候选人简历，安排 {}", viewerUserId, scheduleId);
            throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
        }

        return resumeService.getResumeWithFieldValuesById(schedule.getResumeId());
    }

    @Override
    public boolean isInterviewerOfCycle(Integer cycleId, Integer userId) {
        if (cycleId == null || userId == null) {
            return false;
        }
        // 该用户绑定的全部场次 -> 取出其中属于本周期的
        List<SessionInterviewer> bindings = sessionInterviewerMapper.selectList(
                new LambdaQueryWrapper<SessionInterviewer>().eq(SessionInterviewer::getUserId, userId));
        if (bindings.isEmpty()) {
            return false;
        }
        List<Integer> sessionIds = bindings.stream()
                .map(SessionInterviewer::getSessionId)
                .filter(Objects::nonNull)
                .toList();
        if (sessionIds.isEmpty()) {
            return false;
        }
        return interviewSessionMapper.exists(new LambdaQueryWrapper<InterviewSession>()
                .in(InterviewSession::getSessionId, sessionIds)
                .eq(InterviewSession::getCycleId, cycleId));
    }

    private boolean isInterviewerOf(Integer sessionId, Integer userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        return sessionInterviewerMapper.exists(new LambdaQueryWrapper<SessionInterviewer>()
                .eq(SessionInterviewer::getSessionId, sessionId)
                .eq(SessionInterviewer::getUserId, userId));
    }

    private List<EvaluationBoardSeedDTO.RowSeed> buildRows(Integer cycleId) {
        List<InterviewSchedule> roster = listRoster(cycleId);
        if (roster.isEmpty()) {
            return Collections.emptyList();
        }

        // user_id 是后补的列，历史行可能为空，用简历兜底带出候选人
        Map<Integer, Integer> resumeToUser = resolveMissingUserIds(roster);

        Set<Integer> userIds = roster.stream()
                .map(s -> s.getUserId() != null ? s.getUserId() : resumeToUser.get(s.getResumeId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : selectUsersByIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));

        Set<Integer> deptIds = roster.stream()
                .map(InterviewSchedule::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Department> departments = deptIds.isEmpty() ? Collections.emptyMap()
                : departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getDeptId, Function.identity(), (a, b) -> a));

        Map<Integer, List<Integer>> interviewersBySession = loadInterviewersBySession(roster);

        List<EvaluationBoardSeedDTO.RowSeed> rows = new ArrayList<>(roster.size());
        for (InterviewSchedule schedule : roster) {
            Integer userId = schedule.getUserId() != null
                    ? schedule.getUserId()
                    : resumeToUser.get(schedule.getResumeId());
            User user = userId == null ? null : users.get(userId);
            Department department = schedule.getDeptId() == null ? null : departments.get(schedule.getDeptId());

            EvaluationBoardSeedDTO.RowSeed row = new EvaluationBoardSeedDTO.RowSeed();
            row.setScheduleId(schedule.getScheduleId());
            row.setResumeId(schedule.getResumeId());
            row.setUserId(userId);
            row.setCandidateName(user == null ? null : user.getName());
            row.setAccount(user == null ? null : user.getUsername());
            row.setDeptId(schedule.getDeptId());
            row.setDeptName(department == null ? null : department.getDeptName());
            row.setSessionId(schedule.getSessionId());
            row.setInterviewTime(schedule.getInterviewTime());
            row.setInterviewerUserIds(schedule.getSessionId() == null
                    ? Collections.emptyList()
                    : interviewersBySession.getOrDefault(schedule.getSessionId(), Collections.emptyList()));
            rows.add(row);
        }
        return rows;
    }

    private Map<Integer, Integer> resolveMissingUserIds(List<InterviewSchedule> roster) {
        Set<Integer> resumeIds = roster.stream()
                .filter(s -> s.getUserId() == null && s.getResumeId() != null)
                .map(InterviewSchedule::getResumeId)
                .collect(Collectors.toSet());
        if (resumeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return resumeMapper.selectBatchIds(resumeIds).stream()
                .filter(r -> r.getUserId() != null)
                .collect(Collectors.toMap(Resume::getResumeId, Resume::getUserId, (a, b) -> a));
    }

    private Map<Integer, List<Integer>> loadInterviewersBySession(List<InterviewSchedule> roster) {
        Set<Integer> sessionIds = roster.stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SessionInterviewer> bindings = sessionInterviewerMapper.selectList(
                new LambdaQueryWrapper<SessionInterviewer>()
                        .in(SessionInterviewer::getSessionId, sessionIds)
                        .orderByAsc(SessionInterviewer::getId));
        Map<Integer, List<Integer>> bySession = new HashMap<>();
        for (SessionInterviewer binding : bindings) {
            bySession.computeIfAbsent(binding.getSessionId(), k -> new ArrayList<>()).add(binding.getUserId());
        }
        return bySession;
    }

    /**
     * 按 ID 批量取用户，必须走 {@link UserMapper#selectByIds} 这个自定义查询。
     * <p>
     * MyBatis-Plus 的通用查询（{@code selectBatchIds} / {@code selectList}）在 User 上都用不了：
     * 实体声明了 {@code @TableField("role")}，而 user 表在 V6 之后已无 role 列（角色移到 user_role），
     * 通用查询拼出的列清单会撞上「Unknown column 'role'」。自定义 XML 用显式 resultMap 避开了这点。
     */
    private List<User> selectUsersByIds(Collection<Integer> userIds) {
        return userMapper.selectByIds(new ArrayList<>(userIds));
    }

    private List<InterviewSchedule> listRoster(Integer cycleId) {
        return interviewScheduleMapper.selectList(new LambdaQueryWrapper<InterviewSchedule>()
                .eq(InterviewSchedule::getCycleId, cycleId)
                .eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE)
                .orderByAsc(InterviewSchedule::getInterviewTime)
                .orderByAsc(InterviewSchedule::getScheduleId));
    }

    private List<EvaluationDimension> listDimensions(Integer cycleId) {
        return evaluationDimensionMapper.selectList(new LambdaQueryWrapper<EvaluationDimension>()
                .eq(EvaluationDimension::getCycleId, cycleId)
                .orderByAsc(EvaluationDimension::getSortOrder)
                .orderByAsc(EvaluationDimension::getDimensionId));
    }

    private CollabDoc requireDoc(Integer cycleId) {
        CollabDoc doc = collabDocMapper.selectById(docName(cycleId));
        if (doc == null) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED);
        }
        return doc;
    }

    private void requireCycle(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }

    private EvaluationBoardDTO toBoardDTO(CollabDoc doc, int rowCount, int dimensionCount) {
        EvaluationBoardDTO dto = new EvaluationBoardDTO();
        dto.setCycleId(doc.getCycleId());
        dto.setDocName(doc.getDocName());
        dto.setLocked(Integer.valueOf(1).equals(doc.getLocked()));
        dto.setRowCount(rowCount);
        dto.setDimensionCount(dimensionCount);
        dto.setUpdatedAt(doc.getUpdatedAt());
        return dto;
    }

    static String docName(Integer cycleId) {
        return DOC_NAME_PREFIX + cycleId;
    }
}
