package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.EvaluationSummaryDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationRequestDTO;
import club.boyuan.official.domain.interview.dto.MaterializeEvaluationResultDTO;
import club.boyuan.official.domain.interview.service.IInterviewEvaluationService;
import club.boyuan.official.persistence.entity.CollabDoc;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.EvaluationDimension;
import club.boyuan.official.persistence.entity.InterviewEvaluation;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.CollabDocMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.InterviewPreferenceMapper;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewPreference;
import club.boyuan.official.persistence.mapper.EvaluationDimensionMapper;
import club.boyuan.official.persistence.mapper.InterviewEvaluationMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
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

    private static final int SCHEDULE_STATUS_ACTIVE = 1;

    private final InterviewEvaluationMapper interviewEvaluationMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;
    private final EvaluationDimensionMapper evaluationDimensionMapper;
    private final CollabDocMapper collabDocMapper;
    private final ResumeMapper resumeMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewPreferenceMapper interviewPreferenceMapper;
    private final ObjectMapper objectMapper;
    private final club.boyuan.official.persistence.mapper.EvaluationScopeMapper evaluationScopeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterializeEvaluationResultDTO materialize(MaterializeEvaluationRequestDTO request) {
        Integer cycleId = request.getCycleId();
        String expectedDocName = EvaluationBoardServiceImpl.docName(cycleId);
        if (!expectedDocName.equals(request.getDocName())) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED,
                    "文档名与周期不匹配：" + request.getDocName());
        }

        if (request.getSnapshotRevision() == null) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "缺少已提交的协作快照版本");
        }
        CollabDoc doc = collabDocMapper.lockSnapshot(expectedDocName);
        if (doc == null) throw new BusinessException(BusinessExceptionEnum.EVALUATION_BOARD_NOT_OPENED);
        if (doc.getSnapshotRevision() == null || doc.getSnapshotRevision() < 1) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "协作快照尚未提交");
        }
        return materializeCommitted(doc, request);
    }

    private MaterializeEvaluationResultDTO materializeCommitted(CollabDoc doc, MaterializeEvaluationRequestDTO request) {
        var result = new MaterializeEvaluationResultDTO();
        Long version = request.getSnapshotRevision();
        if (version != null && version < doc.getSnapshotRevision()) return result;
        if (!doc.getSnapshotRevision().equals(version))
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "快照版本不一致");
        Map<Integer, BigDecimal> weights = listDimensions(doc.getCycleId()).stream()
                .collect(Collectors.toMap(EvaluationDimension::getDimensionId, EvaluationDimension::getWeight));
        com.fasterxml.jackson.databind.JsonNode rows;
        try {
            rows = objectMapper.readTree(doc.getAuthorState()).required("rows");
        } catch (Exception error) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "已提交快照缺少署名");
        }
        for (var supplied : request.getItems()) {
            var row = rows.path(String.valueOf(supplied.getScheduleId()));
            if (supplied.getVersion() == null || row.path("editedRevision").asLong() != supplied.getVersion() || supplied.getVersion() > version)
                throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "评价行版本无效");
            MaterializeEvaluationRequestDTO.EvaluationItem item;
            try {
                item = objectMapper.treeToValue(row.required("materialized"), MaterializeEvaluationRequestDTO.EvaluationItem.class);
            } catch (Exception error) {
                throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "评价行快照无效");
            }
            if (!supplied.equals(item) || item.getContributors() == null || item.getLastEditedBy() == null
                    || !item.getContributors().contains(item.getLastEditedBy()))
                throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "实际作者或评价内容与已提交快照不一致");
            var schedule = interviewScheduleMapper.selectById(item.getScheduleId());
            if (schedule == null || !doc.getCycleId().equals(schedule.getCycleId())) continue;
            var existing = interviewEvaluationMapper.selectOne(new LambdaQueryWrapper<InterviewEvaluation>()
                    .eq(InterviewEvaluation::getScheduleId, item.getScheduleId()).last("FOR UPDATE"));
            if (existing != null && existing.getVersion() != null && existing.getVersion() >= item.getVersion()) continue;
            var contributors = new LinkedHashSet<>(item.getContributors());
            var entity = buildEntity(item, schedule, doc.getCycleId(), weights, contributors);
            if (item.getSubmittedAt() != null && entity.getSubmittedBy() != null) {
                String stamp = item.getSubmittedAt();
                entity.setSubmittedAt(stamp.endsWith("Z")
                        ? java.time.Instant.parse(stamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime()
                        : LocalDateTime.parse(stamp));
            } else entity.setSubmittedAt(null);
            interviewEvaluationMapper.upsertMaterialized(entity);
            result.setAccepted(result.getAccepted()+1);
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

        int evaluatorCount = evaluationScopeMapper.evaluatorIds().size();
        Map<Integer, User> users = loadUsers(roster, evaluationBySchedule.values());
        Map<Integer, Integer> resumeToUser = resolveMissingUserIds(roster);
        Map<Integer, Department> departments = loadDepartments(roster);
        // 录取决策要按地点与志愿分组看，两者都不在 interview_schedule 上：
        // 地点属于场次，志愿在 interview_preference（按简历号）
        Map<Integer, String> locationBySession = loadLocations(roster);
        Map<Integer, InterviewPreference> preferenceByResume = loadPreferences(roster);

        for (InterviewSchedule schedule : roster) {
            summary.getCandidates().add(buildCandidateSummary(
                    schedule,
                    evaluationBySchedule.get(schedule.getScheduleId()),
                    evaluatorCount,
                    users, resumeToUser, departments,
                    locationBySession, preferenceByResume));
        }
        return summary;
    }

    private EvaluationSummaryDTO.CandidateSummary buildCandidateSummary(
            InterviewSchedule schedule,
            InterviewEvaluation evaluation,
            int assignedInterviewerCount,
            Map<Integer, User> users,
            Map<Integer, Integer> resumeToUser,
            Map<Integer, Department> departments,
            Map<Integer, String> locationBySession,
            Map<Integer, InterviewPreference> preferenceByResume) {

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
        candidateSummary.setLocation(schedule.getSessionId() == null
                ? null : locationBySession.get(schedule.getSessionId()));
        InterviewPreference preference = schedule.getResumeId() == null
                ? null : preferenceByResume.get(schedule.getResumeId());
        if (preference != null) {
            candidateSummary.setFirstDeptName(deptName(departments, preference.getFirstDeptId()));
            candidateSummary.setSecondDeptName(deptName(departments, preference.getSecondDeptId()));
        }

        if (evaluation == null) {
            return candidateSummary;
        }

        candidateSummary.setScores(parseScores(evaluation.getScores()));
        candidateSummary.setTotalScore(evaluation.getTotalScore());
        candidateSummary.setDimensionNotes(parseStringMap(evaluation.getDimensionNotes()));
        candidateSummary.setDimensionWriters(buildDimensionWriters(evaluation.getDimensionWriters(), users));
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
                                            Collection<Integer> contributors) {
        Map<Integer, BigDecimal> scores = item.getScores() == null
                ? Collections.emptyMap()
                : item.getScores();
        int status = item.getStatus() == null ? InterviewEvaluation.STATUS_DRAFT : item.getStatus();
        Integer submittedBy = status == InterviewEvaluation.STATUS_SUBMITTED ? item.getSubmittedBy() : null;
        Integer lastEditedBy = item.getLastEditedBy();
        Map<Integer, Integer> dimensionWriters = item.getDimensionWriters() == null
                ? Collections.emptyMap() : item.getDimensionWriters();
        Map<Integer, String> dimensionNotes = item.getDimensionNotes() == null
                ? Collections.emptyMap()
                : item.getDimensionNotes();

        return new InterviewEvaluation()
                .setScheduleId(item.getScheduleId())
                .setCycleId(cycleId)
                .setResumeId(schedule.getResumeId())
                .setScores(writeScores(scores))
                .setTotalScore(weightedTotal(scores, weights))
                .setDimensionNotes(writeJson(dimensionNotes, "维度评语"))
                .setDimensionWriters(writeJson(dimensionWriters, "维度作者"))
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
        // 必须走自定义的 selectUsersByIds：User 实体带着已不存在的 role 列，
        // MyBatis-Plus 的通用查询会拼出「Unknown column 'role'」
        return userMapper.selectUsersByIds(new ArrayList<>(userIds)).stream()
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
        /*
         * 取全部部门而不是只取名单里出现过的 deptId。
         *
         * 志愿部门（interview_preference.first/second_dept_id）未必在名单的
         * deptId 集合里 —— 一个人第一志愿技术部、被调剂去了项目部，技术部就不在那组 id 中，
         * 按 id 批量查会漏掉它，志愿列显示成空。部门表本来就只有个位数行，全取不值一提。
         */
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getDeptId, Function.identity(), (a, b) -> a));
    }

    /** 场次ID → 面试地点。线上面试没有场次，取不到就是 null */
    private Map<Integer, String> loadLocations(List<InterviewSchedule> roster) {
        Set<Integer> sessionIds = roster.stream()
                .map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return interviewSessionMapper.selectBatchIds(sessionIds).stream()
                .filter(session -> session.getLocation() != null)
                .collect(Collectors.toMap(InterviewSession::getSessionId,
                        InterviewSession::getLocation, (a, b) -> a));
    }

    /** 简历号 → 志愿（第一 / 第二部门） */
    private Map<Integer, InterviewPreference> loadPreferences(List<InterviewSchedule> roster) {
        Set<Integer> resumeIds = roster.stream()
                .map(InterviewSchedule::getResumeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (resumeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return interviewPreferenceMapper.selectList(new LambdaQueryWrapper<InterviewPreference>()
                        .in(InterviewPreference::getResumeId, resumeIds))
                .stream()
                .collect(Collectors.toMap(InterviewPreference::getResumeId, Function.identity(), (a, b) -> a));
    }

    private static String deptName(Map<Integer, Department> departments, Integer deptId) {
        Department dept = deptId == null ? null : departments.get(deptId);
        return dept == null ? null : dept.getDeptName();
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

    /** 解析 {dimensionId: text} */
    private Map<Integer, String> parseStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<Integer, String>>() {});
        } catch (Exception e) {
            log.warn("维度评语解析失败，按空处理：{}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 解析 {dimensionId: userId} 并补上姓名，供管理端显示「这一项是谁评的」 */
    private Map<Integer, EvaluationSummaryDTO.Contributor> buildDimensionWriters(String json,
                                                                                Map<Integer, User> users) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> raw;
        try {
            raw = objectMapper.readValue(json, new TypeReference<Map<Integer, Integer>>() {});
        } catch (Exception e) {
            log.warn("维度作者解析失败，按空处理：{}", e.getMessage());
            return Collections.emptyMap();
        }
        Map<Integer, EvaluationSummaryDTO.Contributor> result = new LinkedHashMap<>();
        raw.forEach((dimensionId, userId) -> {
            if (userId == null) {
                return;
            }
            EvaluationSummaryDTO.Contributor c = new EvaluationSummaryDTO.Contributor();
            c.setUserId(userId);
            c.setName(userName(users, userId));
            result.put(dimensionId, c);
        });
        return result;
    }

    /** 通用 JSON 序列化：空表存 null，避免库里落一堆 "{}" */
    private String writeJson(Map<?, ?> value, String what) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, what + "序列化失败：" + e.getMessage());
        }
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
