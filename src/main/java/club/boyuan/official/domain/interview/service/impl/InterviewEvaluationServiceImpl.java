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
import java.util.ArrayList;
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

        MaterializeEvaluationResultDTO result = new MaterializeEvaluationResultDTO();
        Map<Integer, List<Integer>> acceptedByUser = new LinkedHashMap<>();
        Map<Integer, List<Integer>> rejectedByUser = new LinkedHashMap<>();

        for (MaterializeEvaluationRequestDTO.EvaluationItem item : request.getItems()) {
            InterviewSchedule schedule = schedules.get(item.getScheduleId());
            if (schedule == null || !cycleId.equals(schedule.getCycleId())) {
                reject(result, rejectedByUser, item,
                        "面试安排 " + item.getScheduleId() + " 不存在或不属于周期 " + cycleId);
                continue;
            }

            // CRDT 协议层拦不住越权写入，这里是第二道闸：单元格键上的面试官必须就是实际写入者
            if (!Objects.equals(item.getOriginUserId(), item.getInterviewerUserId())) {
                reject(result, rejectedByUser, item,
                        "用户 " + item.getOriginUserId() + " 试图写入面试官 " + item.getInterviewerUserId()
                                + " 在安排 " + item.getScheduleId() + " 上的评价");
                continue;
            }

            interviewEvaluationMapper.upsertMaterialized(buildEntity(item, schedule, cycleId, weights));
            result.setAccepted(result.getAccepted() + 1);
            acceptedByUser.computeIfAbsent(item.getOriginUserId(), k -> new ArrayList<>())
                    .add(item.getScheduleId());
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

        Map<Integer, List<InterviewEvaluation>> evaluationsBySchedule = interviewEvaluationMapper.selectList(
                        new LambdaQueryWrapper<InterviewEvaluation>()
                                .eq(InterviewEvaluation::getCycleId, cycleId))
                .stream()
                .collect(Collectors.groupingBy(InterviewEvaluation::getScheduleId));

        Map<Integer, Integer> expectedBySchedule = countExpectedEvaluations(roster);
        Map<Integer, User> users = loadUsers(roster, evaluationsBySchedule);
        Map<Integer, Integer> resumeToUser = resolveMissingUserIds(roster);
        Map<Integer, Department> departments = loadDepartments(roster);

        for (InterviewSchedule schedule : roster) {
            summary.getCandidates().add(buildCandidateSummary(
                    schedule, dimensions,
                    evaluationsBySchedule.getOrDefault(schedule.getScheduleId(), Collections.emptyList()),
                    expectedBySchedule.getOrDefault(schedule.getScheduleId(), 0),
                    users, resumeToUser, departments));
        }
        return summary;
    }

    private EvaluationSummaryDTO.CandidateSummary buildCandidateSummary(
            InterviewSchedule schedule,
            List<EvaluationDimension> dimensions,
            List<InterviewEvaluation> evaluations,
            int expectedCount,
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
        candidateSummary.setExpectedCount(expectedCount);

        Map<Integer, List<BigDecimal>> scoresByDimension = new LinkedHashMap<>();
        List<BigDecimal> totals = new ArrayList<>();
        int submitted = 0;

        for (InterviewEvaluation evaluation : evaluations) {
            Map<Integer, BigDecimal> scores = parseScores(evaluation.getScores());
            EvaluationSummaryDTO.InterviewerEvaluation detail = new EvaluationSummaryDTO.InterviewerEvaluation();
            detail.setInterviewerUserId(evaluation.getInterviewerUserId());
            User interviewer = users.get(evaluation.getInterviewerUserId());
            detail.setInterviewerName(interviewer == null ? null : interviewer.getName());
            detail.setScores(scores);
            detail.setTotalScore(evaluation.getTotalScore());
            detail.setComment(evaluation.getComment());
            detail.setRecommendation(evaluation.getRecommendation());
            detail.setStatus(evaluation.getStatus());
            candidateSummary.getEvaluations().add(detail);

            if (InterviewEvaluation.STATUS_SUBMITTED == (evaluation.getStatus() == null ? 0 : evaluation.getStatus())) {
                submitted++;
            }
            if (evaluation.getRecommendation() != null) {
                candidateSummary.getRecommendationCounts().merge(evaluation.getRecommendation(), 1, Integer::sum);
            }
            if (evaluation.getTotalScore() != null) {
                totals.add(evaluation.getTotalScore());
            }
            scores.forEach((dimensionId, score) -> {
                if (score != null) {
                    scoresByDimension.computeIfAbsent(dimensionId, k -> new ArrayList<>()).add(score);
                }
            });
        }

        candidateSummary.setSubmittedCount(submitted);
        for (EvaluationDimension dimension : dimensions) {
            List<BigDecimal> values = scoresByDimension.get(dimension.getDimensionId());
            if (values != null && !values.isEmpty()) {
                candidateSummary.getDimensionAverages().put(dimension.getDimensionId(), average(values));
            }
        }
        if (!totals.isEmpty()) {
            candidateSummary.setAverageTotalScore(average(totals));
        }
        return candidateSummary;
    }

    private InterviewEvaluation buildEntity(MaterializeEvaluationRequestDTO.EvaluationItem item,
                                            InterviewSchedule schedule,
                                            Integer cycleId,
                                            Map<Integer, BigDecimal> weights) {
        Map<Integer, BigDecimal> scores = item.getScores() == null
                ? Collections.emptyMap()
                : item.getScores();
        return new InterviewEvaluation()
                .setScheduleId(item.getScheduleId())
                .setCycleId(cycleId)
                .setResumeId(schedule.getResumeId())
                .setInterviewerUserId(item.getInterviewerUserId())
                .setScores(writeScores(scores))
                .setTotalScore(weightedTotal(scores, weights))
                .setComment(item.getComment())
                .setRecommendation(item.getRecommendation())
                .setStatus(item.getStatus() == null ? InterviewEvaluation.STATUS_DRAFT : item.getStatus())
                .setVersion(item.getVersion());
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

    private void reject(MaterializeEvaluationResultDTO result,
                        Map<Integer, List<Integer>> rejectedByUser,
                        MaterializeEvaluationRequestDTO.EvaluationItem item,
                        String reason) {
        result.setRejected(result.getRejected() + 1);
        result.getRejectReasons().add(reason);
        rejectedByUser.computeIfAbsent(item.getOriginUserId(), k -> new ArrayList<>())
                .add(item.getScheduleId());
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
                                         Map<Integer, List<InterviewEvaluation>> evaluationsBySchedule) {
        Set<Integer> userIds = roster.stream()
                .map(InterviewSchedule::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        evaluationsBySchedule.values().stream()
                .flatMap(List::stream)
                .map(InterviewEvaluation::getInterviewerUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
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
}
