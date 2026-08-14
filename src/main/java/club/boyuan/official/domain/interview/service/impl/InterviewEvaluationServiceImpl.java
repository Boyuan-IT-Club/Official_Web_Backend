package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.EvaluationSummaryDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationResultDTO;
import club.boyuan.official.domain.interview.service.IInterviewEvaluationService;
import club.boyuan.official.persistence.entity.CollabAudit;
import club.boyuan.official.persistence.entity.CollabDoc;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.EvaluationDimension;
import club.boyuan.official.persistence.entity.InterviewEvaluation;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.CollabAuditMapper;
import club.boyuan.official.persistence.mapper.CollabDocMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.EvaluationDimensionMapper;
import club.boyuan.official.persistence.mapper.InterviewEvaluationMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 面试评价物化与汇总实现。
 *
 * @author dhy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationServiceImpl implements IInterviewEvaluationService {

    /** touched_keys 列长度上限，超出截断 */
    private static final int TOUCHED_KEYS_MAX_LENGTH = 512;

    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final InterviewEvaluationMapper interviewEvaluationMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;
    private final EvaluationDimensionMapper evaluationDimensionMapper;
    private final SessionInterviewerMapper sessionInterviewerMapper;
    private final CollabDocMapper collabDocMapper;
    private final CollabAuditMapper collabAuditMapper;
    private final ResumeMapper resumeMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterializeEvaluationResultDTO materialize(MaterializeEvaluationRequestDTO request) {
        Integer cycleId = request.getCycleId();
        String expectedDocName = EvaluationBoardServiceImpl.docName(cycleId);
        if (!expectedDocName.equals(request.getDocName())) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED,
                    "文档名与周期不匹配：" + request.getDocName());
        }

        CollabDoc doc = collabDocMapper.selectById(expectedDocName);
        if (doc == null) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED);
        }
        if (Integer.valueOf(1).equals(doc.getLocked())) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_LOCKED);
        }

        Map<Integer, BigDecimal> weights = listDimensions(cycleId).stream()
                .collect(Collectors.toMap(EvaluationDimension::getDimensionId, EvaluationDimension::getWeight,
                        (a, b) -> a));

        Set<Integer> scheduleIds = request.getItems().stream()
                .map(MaterializeEvaluationRequestDTO.EvaluationItem::getScheduleId)
                .collect(Collectors.toSet());
        Map<Integer, InterviewSchedule> schedules = scheduleIds.isEmpty() ? Collections.emptyMap()
                : interviewScheduleMapper.selectBatchIds(scheduleIds).stream()
                .collect(Collectors.toMap(InterviewSchedule::getScheduleId, Function.identity(), (a, b) -> a));

        // 场次 → 绑定的面试官，用于判定「这个人有没有资格改这一行」
        Map<Integer, Set<Integer>> interviewersBySession = loadInterviewersBySession(schedules.values());

        // 协同服务重启后 tracker 从零开始，这一轮带来的参与人只是重启之后动过手的那批。
        // 署名要的是「一共有谁参与过」，因此取回库中已记录的并集，而不是直接覆盖。
        Map<Integer, List<Integer>> existingContributors = scheduleIds.isEmpty() ? Collections.emptyMap()
                : interviewEvaluationMapper.selectList(new LambdaQueryWrapper<InterviewEvaluation>()
                        .in(InterviewEvaluation::getScheduleId, scheduleIds))
                .stream()
                .collect(Collectors.toMap(InterviewEvaluation::getScheduleId,
                        e -> parseContributors(e.getContributors()), (a, b) -> a));

        MaterializeEvaluationResultDTO result = new MaterializeEvaluationResultDTO();
        Map<Integer, List<Integer>> acceptedByUser = new LinkedHashMap<>();
        Map<Integer, List<Integer>> rejectedByUser = new LinkedHashMap<>();

        for (MaterializeEvaluationRequestDTO.EvaluationItem item : request.getItems()) {
            InterviewSchedule schedule = schedules.get(item.getScheduleId());
            if (schedule == null || !cycleId.equals(schedule.getCycleId())) {
                rejectRow(result, rejectedByUser, item,
                        "面试安排 " + item.getScheduleId() + " 不存在或不属于周期 " + cycleId);
                continue;
            }

            // CRDT 协议层拦不住越权写入，这里是第二道闸：改这一行的人必须绑定在该行所属场次上。
            //
            // 共编模型下无法把某个字段的值回滚给特定的人——多人写同一格，CRDT 收敛后已分不出谁写了哪个字符。
            // 因此这里的处理是：仍然落库（否则一个捣乱者就能让整场面试的记录都写不进去），
            // 但把未授权者从 contributors 里剔除、单独记一条 rejected 审计，由管理员事后追查。
            Set<Integer> allowed = interviewersBySession.getOrDefault(schedule.getSessionId(), Collections.emptySet());
            Set<Integer> contributors = new LinkedHashSet<>(
                    existingContributors.getOrDefault(item.getScheduleId(), Collections.emptyList()));
            for (Integer contributor : item.getContributors() == null ? List.<Integer>of() : item.getContributors()) {
                if (allowed.contains(contributor)) {
                    contributors.add(contributor);
                } else {
                    result.setRejected(result.getRejected() + 1);
                    result.getRejectReasons().add("用户 " + contributor + " 未绑定在安排 "
                            + item.getScheduleId() + " 所属场次上，已从参与人中剔除");
                    rejectedByUser.computeIfAbsent(contributor, k -> new ArrayList<>()).add(item.getScheduleId());
                }
            }

            interviewEvaluationMapper.upsertMaterialized(
                    buildEntity(item, schedule, cycleId, weights, contributors, allowed));
            result.setAccepted(result.getAccepted() + 1);
            for (Integer contributor : contributors) {
                acceptedByUser.computeIfAbsent(contributor, k -> new ArrayList<>()).add(item.getScheduleId());
            }
        }

        writeAudit(expectedDocName, acceptedByUser, 0);
        writeAudit(expectedDocName, rejectedByUser, 1);

        if (result.getRejected() > 0) {
            log.warn("文档 {} 物化丢弃 {} 条越权或无效数据：{}",
                    expectedDocName, result.getRejected(), result.getRejectReasons());
        }
        return result;
    }

    @Override
    public EvaluationSummaryDTO summary(Integer cycleId) {
        List<EvaluationDimension> dimensions = listDimensions(cycleId);
        EvaluationSummaryDTO summary = new EvaluationSummaryDTO();
        summary.setCycleId(cycleId);
        summary.setDimensions(dimensions.stream()
                .map(EvaluationDimensionServiceImpl::toDTO)
                .collect(Collectors.toList()));

        List<InterviewSchedule> roster = interviewScheduleMapper.selectList(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, SCHEDULE_STATUS_ACTIVE)
                        .orderByAsc(InterviewSchedule::getInterviewTime)
                        .orderByAsc(InterviewSchedule::getScheduleId));
        if (roster.isEmpty()) {
            return summary;
        }

        // 一场面试一份评价，按 schedule_id 取即可
        Map<Integer, InterviewEvaluation> evaluationBySchedule = interviewEvaluationMapper.selectList(
                        new LambdaQueryWrapper<InterviewEvaluation>()
                                .eq(InterviewEvaluation::getCycleId, cycleId))
                .stream()
                .collect(Collectors.toMap(InterviewEvaluation::getScheduleId, Function.identity(), (a, b) -> a));

        Map<Integer, Integer> assignedBySchedule = countExpectedEvaluations(roster);
        Map<Integer, User> users = loadUsers(roster, evaluationBySchedule.values());
        Map<Integer, Integer> resumeToUser = resolveMissingUserIds(roster);
        Map<Integer, Department> departments = loadDepartments(roster);

        for (InterviewSchedule schedule : roster) {
            summary.getCandidates().add(buildCandidateSummary(
                    schedule,
                    evaluationBySchedule.get(schedule.getScheduleId()),
                    assignedBySchedule.getOrDefault(schedule.getScheduleId(), 0),
                    users, resumeToUser, departments));
        }
        return summary;
    }

    private EvaluationSummaryDTO.CandidateSummary buildCandidateSummary(
            InterviewSchedule schedule,
            InterviewEvaluation evaluation,
            int assignedInterviewerCount,
            Map<Integer, User> users,
            Map<Integer, Integer> resumeToUser,
            Map<Integer, Department> departments) {

        Integer userId = schedule.getUserId() != null
                ? schedule.getUserId()
                : resumeToUser.get(schedule.getResumeId());
        User candidate = userId == null ? null : users.get(userId);
        Department department = schedule.getDeptId() == null ? null : departments.get(schedule.getDeptId());

        EvaluationSummaryDTO.CandidateSummary candidateSummary = new EvaluationSummaryDTO.CandidateSummary();
        candidateSummary.setScheduleId(schedule.getScheduleId());
        candidateSummary.setResumeId(schedule.getResumeId());
        candidateSummary.setUserId(userId);
        candidateSummary.setCandidateName(candidate == null ? null : candidate.getName());
        candidateSummary.setDeptName(department == null ? null : department.getDeptName());
        candidateSummary.setInterviewTime(schedule.getInterviewTime());
        candidateSummary.setAssignedInterviewerCount(assignedInterviewerCount);

        if (evaluation == null) {
            return candidateSummary;
        }

        candidateSummary.setScores(parseScores(evaluation.getScores()));
        candidateSummary.setTotalScore(evaluation.getTotalScore());
        candidateSummary.setComment(evaluation.getComment());
        candidateSummary.setRecommendation(evaluation.getRecommendation());
        candidateSummary.setStatus(evaluation.getStatus());
        candidateSummary.setLastEditedBy(evaluation.getLastEditedBy());
        candidateSummary.setLastEditedByName(userName(users, evaluation.getLastEditedBy()));
        candidateSummary.setSubmittedBy(evaluation.getSubmittedBy());
        candidateSummary.setSubmittedByName(userName(users, evaluation.getSubmittedBy()));
        candidateSummary.setSubmittedAt(evaluation.getSubmittedAt());

        for (Integer contributorId : parseContributors(evaluation.getContributors())) {
            EvaluationSummaryDTO.Contributor contributor = new EvaluationSummaryDTO.Contributor();
            contributor.setUserId(contributorId);
            contributor.setName(userName(users, contributorId));
            candidateSummary.getContributors().add(contributor);
        }
        return candidateSummary;
    }

    private String userName(Map<Integer, User> users, Integer userId) {
        User user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getName();
    }

    private InterviewEvaluation buildEntity(MaterializeEvaluationRequestDTO.EvaluationItem item,
                                            InterviewSchedule schedule,
                                            Integer cycleId,
                                            Map<Integer, BigDecimal> weights,
                                            Collection<Integer> contributors,
                                            Set<Integer> allowed) {
        Map<Integer, BigDecimal> scores = item.getScores() == null
                ? Collections.emptyMap()
                : item.getScores();
        int status = item.getStatus() == null ? InterviewEvaluation.STATUS_DRAFT : item.getStatus();
        // 定稿人同样要校验绑定，否则未授权者点一下定稿就能把自己署上去
        Integer submittedBy = status == InterviewEvaluation.STATUS_SUBMITTED
                && item.getSubmittedBy() != null && allowed.contains(item.getSubmittedBy())
                ? item.getSubmittedBy()
                : null;
        Integer lastEditedBy = item.getLastEditedBy() != null && allowed.contains(item.getLastEditedBy())
                ? item.getLastEditedBy()
                : null;
        return new InterviewEvaluation()
                .setScheduleId(item.getScheduleId())
                .setCycleId(cycleId)
                .setResumeId(schedule.getResumeId())
                .setScores(writeScores(scores))
                .setTotalScore(weightedTotal(scores, weights))
                .setComment(item.getComment())
                .setRecommendation(item.getRecommendation())
                .setStatus(status)
                .setContributors(writeContributors(contributors))
                .setLastEditedBy(lastEditedBy)
                .setSubmittedBy(submittedBy)
                .setSubmittedAt(submittedBy == null ? null : LocalDateTime.now())
                .setVersion(item.getVersion());
    }

    /**
     * 场次 → 绑定的面试官集合。空集合意味着该场次还没绑人，此时任何写入都会被剔除，
     * 这是刻意的：没绑面试官的场次不该出现评价数据，出现了就该被看见。
     */
    private Map<Integer, Set<Integer>> loadInterviewersBySession(Collection<InterviewSchedule> schedules) {
        Set<Integer> sessionIds = schedules.stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Set<Integer>> bySession = new HashMap<>();
        for (SessionInterviewer binding : sessionInterviewerMapper.selectList(
                new LambdaQueryWrapper<SessionInterviewer>().in(SessionInterviewer::getSessionId, sessionIds))) {
            bySession.computeIfAbsent(binding.getSessionId(), k -> new LinkedHashSet<>()).add(binding.getUserId());
        }
        return bySession;
    }

    /**
     * 加权总分 = Σ(维度得分 × 维度权重)。
     * 不在本周期维度表里的分数会被忽略——维度可能已被管理员删除，但历史 scores JSON 仍保留着它。
     */
    private BigDecimal weightedTotal(Map<Integer, BigDecimal> scores, Map<Integer, BigDecimal> weights) {
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean scored = false;
        for (Map.Entry<Integer, BigDecimal> entry : scores.entrySet()) {
            BigDecimal weight = weights.get(entry.getKey());
            if (weight == null || entry.getValue() == null) {
                continue;
            }
            total = total.add(entry.getValue().multiply(weight));
            scored = true;
        }
        return scored ? total.setScale(2, RoundingMode.HALF_UP) : null;
    }

    /** 整行丢弃：安排本身有问题（不存在/跨周期），记在所有参与人名下 */
    private void rejectRow(MaterializeEvaluationResultDTO result,
                           Map<Integer, List<Integer>> rejectedByUser,
                           MaterializeEvaluationRequestDTO.EvaluationItem item,
                           String reason) {
        result.setRejected(result.getRejected() + 1);
        result.getRejectReasons().add(reason);
        for (Integer contributor : item.getContributors() == null ? List.<Integer>of() : item.getContributors()) {
            rejectedByUser.computeIfAbsent(contributor, k -> new ArrayList<>()).add(item.getScheduleId());
        }
    }

    private void writeAudit(String docName, Map<Integer, List<Integer>> scheduleIdsByUser, int rejected) {
        for (Map.Entry<Integer, List<Integer>> entry : scheduleIdsByUser.entrySet()) {
            collabAuditMapper.insert(new CollabAudit()
                    .setDocName(docName)
                    .setUserId(entry.getKey())
                    .setTouchedKeys(truncate(entry.getValue().toString()))
                    .setRejected(rejected));
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= TOUCHED_KEYS_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, TOUCHED_KEYS_MAX_LENGTH - 3) + "...";
    }

    private Map<Integer, Integer> countExpectedEvaluations(List<InterviewSchedule> roster) {
        Set<Integer> sessionIds = roster.stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> countBySession = new HashMap<>();
        for (SessionInterviewer binding : sessionInterviewerMapper.selectList(
                new LambdaQueryWrapper<SessionInterviewer>().in(SessionInterviewer::getSessionId, sessionIds))) {
            countBySession.merge(binding.getSessionId(), 1, Integer::sum);
        }
        Map<Integer, Integer> bySchedule = new HashMap<>();
        for (InterviewSchedule schedule : roster) {
            bySchedule.put(schedule.getScheduleId(),
                    countBySession.getOrDefault(schedule.getSessionId(), 0));
        }
        return bySchedule;
    }

    private Map<Integer, User> loadUsers(List<InterviewSchedule> roster,
                                         Collection<InterviewEvaluation> evaluations) {
        Set<Integer> userIds = roster.stream()
                .map(InterviewSchedule::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 汇总要显示参与人/最后修改人/定稿人的姓名，这些 id 都得一并取回来
        for (InterviewEvaluation evaluation : evaluations) {
            userIds.addAll(parseContributors(evaluation.getContributors()));
            if (evaluation.getLastEditedBy() != null) {
                userIds.add(evaluation.getLastEditedBy());
            }
            if (evaluation.getSubmittedBy() != null) {
                userIds.add(evaluation.getSubmittedBy());
            }
        }
        userIds.addAll(resolveMissingUserIds(roster).values());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 必须走自定义的 selectByIds：User 实体带着已不存在的 role 列，
        // MyBatis-Plus 的通用查询会拼出「Unknown column 'role'」
        return userMapper.selectByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));
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

    private Map<Integer, Department> loadDepartments(List<InterviewSchedule> roster) {
        Set<Integer> deptIds = roster.stream()
                .map(InterviewSchedule::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getDeptId, Function.identity(), (a, b) -> a));
    }

    private List<EvaluationDimension> listDimensions(Integer cycleId) {
        return evaluationDimensionMapper.selectList(new LambdaQueryWrapper<EvaluationDimension>()
                .eq(EvaluationDimension::getCycleId, cycleId)
                .orderByAsc(EvaluationDimension::getSortOrder)
                .orderByAsc(EvaluationDimension::getDimensionId));
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private String writeScores(Map<Integer, BigDecimal> scores) {
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "评分序列化失败：" + e.getMessage());
        }
    }

    private Map<Integer, BigDecimal> parseScores(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<Integer, BigDecimal>>() {
            });
        } catch (Exception e) {
            log.warn("评分 JSON 解析失败，按空处理：{}", json, e);
            return Collections.emptyMap();
        }
    }

    private String writeContributors(Collection<Integer> contributors) {
        try {
            return objectMapper.writeValueAsString(contributors == null ? List.of() : contributors);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "参与人序列化失败：" + e.getMessage());
        }
    }

    private List<Integer> parseContributors(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {
            });
        } catch (Exception e) {
            log.warn("参与人 JSON 解析失败，按空处理：{}", json, e);
            return Collections.emptyList();
        }
    }
}
