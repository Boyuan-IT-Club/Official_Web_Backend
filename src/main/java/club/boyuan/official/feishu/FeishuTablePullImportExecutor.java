package club.boyuan.official.feishu;

import club.boyuan.official.dto.ImportFromFeishuRowResultDTO;
import club.boyuan.official.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.dto.ImportFromFeishuTableResponseDTO;
import club.boyuan.official.entity.Department;
import club.boyuan.official.entity.InterviewResult;
import club.boyuan.official.entity.InterviewSchedule;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.DepartmentService;
import club.boyuan.official.service.IInterviewResultService;
import club.boyuan.official.service.IInterviewScheduleService;
import club.boyuan.official.mapper.ResumeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.IRecruitmentCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从飞书多维表格拉回面试结果并写入平台（批量预加载，避免 N+1）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuTablePullImportExecutor {

    private static final int SCHEDULE_ACTIVE = 1;

    private final FeishuBitableClient feishuBitableClient;
    private final IUserService userService;
    private final ResumeMapper resumeMapper;
    private final IInterviewScheduleService interviewScheduleService;
    private final IInterviewResultService interviewResultService;
    private final DepartmentService departmentService;
    private final IRecruitmentCycleService recruitmentCycleService;

    public ImportFromFeishuTableResponseDTO execute(
            ImportFromFeishuTableRequestDTO request, Integer defaultOperatorUserId) {
        return execute(request, defaultOperatorUserId, null);
    }

    public ImportFromFeishuTableResponseDTO execute(
            ImportFromFeishuTableRequestDTO request,
            Integer defaultOperatorUserId,
            FeishuSyncProgressCallback progress) {
        if (recruitmentCycleService.getRecruitmentCycleById(request.getCycleId()) == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }

        List<FeishuBitableRecord> records = feishuBitableClient.listAllRecords(request.getFeishuTableUrl().trim());
        if (records.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_EMPTY, "飞书表格中没有数据行");
        }

        PullPreloadContext preload = buildPreloadContext(request.getCycleId(), records, defaultOperatorUserId);
        boolean updateUserDept = !Boolean.FALSE.equals(request.getUpdateUserDept());

        ImportFromFeishuTableResponseDTO response = new ImportFromFeishuTableResponseDTO();
        response.setTotalRows(records.size());

        int totalRows = records.size();
        if (progress != null) {
            progress.onProgress(0, totalRows, 0, 0, 0);
        }

        int rowIndex = 0;
        for (FeishuBitableRecord record : records) {
            rowIndex++;
            ImportFromFeishuRowResultDTO rowResult = processRow(
                    record, request.getCycleId(), updateUserDept, defaultOperatorUserId, preload);

            if (!rowResult.isSuccess()) {
                if (rowResult.getMessage() != null && rowResult.getMessage().contains("姓名为空")) {
                    response.setSkippedCount(response.getSkippedCount() + 1);
                } else {
                    response.setFailedCount(response.getFailedCount() + 1);
                }
            } else {
                response.setSuccessCount(response.getSuccessCount() + 1);
            }
            response.getRows().add(rowResult);

            if (progress != null) {
                progress.onProgress(
                        rowIndex,
                        totalRows,
                        response.getSuccessCount(),
                        response.getFailedCount(),
                        response.getSkippedCount());
            }
        }

        log.info("飞书拉回平台完成 cycleId={}, total={}, success={}, failed={}, skipped={}",
                request.getCycleId(), response.getTotalRows(), response.getSuccessCount(),
                response.getFailedCount(), response.getSkippedCount());
        return response;
    }

    private ImportFromFeishuRowResultDTO processRow(
            FeishuBitableRecord record,
            Integer cycleId,
            boolean updateUserDept,
            Integer defaultOperatorUserId,
            PullPreloadContext preload) {
        ImportFromFeishuRowResultDTO rowResult = new ImportFromFeishuRowResultDTO()
                .setRecordId(record.recordId())
                .setName(record.name())
                .setDepartment(record.assignedDept());

        if (!StringUtils.hasText(record.name())) {
            return rowResult.setSuccess(false).setMessage("姓名为空，已跳过");
        }

        int decision = FeishuInterviewResultDecisionResolver.resolve(
                record.interviewPassed(), record.preselectPassed(), record.adjustable());

        Department dept = null;
        if (StringUtils.hasText(record.assignedDept())) {
            dept = resolveDepartment(preload.deptByName, record.assignedDept().trim());
            if (dept == null) {
                return rowResult.setSuccess(false).setMessage("系统中未找到部门: " + record.assignedDept());
            }
        } else if (decision == 1 || decision == 3) {
            return rowResult.setSuccess(false).setMessage("通过/待调剂须填写录取部门");
        }

        try {
            User user = resolveUserInCycle(record.name().trim(), preload);
            rowResult.setUserId(user.getUserId());

            if (updateUserDept && dept != null) {
                user.setDeptId(dept.getDeptId());
                userService.updateById(user);
            }

            Integer decisionBy = resolveDecisionByUserId(record.decisionMakerName(), defaultOperatorUserId, preload);
            Integer assignedDeptId = dept != null ? dept.getDeptId() : null;

            InterviewResult result = upsertInterviewResult(
                    user, assignedDeptId, decision, decisionBy, preload);
            rowResult.setResultId(result.getResultId());
            return rowResult.setSuccess(true).setMessage("导入成功，decision=" + decision);
        } catch (BusinessException ex) {
            return rowResult.setSuccess(false).setMessage(ex.getMessage());
        } catch (Exception ex) {
            log.error("飞书行导入失败 recordId={}, name={}", record.recordId(), record.name(), ex);
            return rowResult.setSuccess(false).setMessage("导入异常: " + ex.getMessage());
        }
    }

    private PullPreloadContext buildPreloadContext(
            Integer cycleId, List<FeishuBitableRecord> records, Integer defaultOperatorUserId) {
        Map<String, Department> deptByName = loadDepartmentByName();

        List<Resume> cycleResumes = resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>().eq(Resume::getCycleId, cycleId));
        Set<Integer> userIdsInCycle = cycleResumes.stream()
                .map(Resume::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Integer, Resume> resumeByUserId = new HashMap<>();
        for (Resume resume : cycleResumes) {
            if (resume.getUserId() != null) {
                resumeByUserId.putIfAbsent(resume.getUserId(), resume);
            }
        }

        Map<String, List<User>> usersByName = new HashMap<>();
        if (!userIdsInCycle.isEmpty()) {
            List<User> users = userService.listByIds(userIdsInCycle);
            for (User user : users) {
                if (user.getUserId() == null || !userIdsInCycle.contains(user.getUserId())) {
                    continue;
                }
                if (!StringUtils.hasText(user.getName())) {
                    continue;
                }
                usersByName.computeIfAbsent(user.getName().trim(), k -> new ArrayList<>()).add(user);
            }
        }

        List<InterviewSchedule> schedules = interviewScheduleService.lambdaQuery()
                .eq(InterviewSchedule::getCycleId, cycleId)
                .eq(InterviewSchedule::getStatus, SCHEDULE_ACTIVE)
                .list();
        Map<Integer, InterviewSchedule> scheduleByResumeId = new HashMap<>();
        for (InterviewSchedule schedule : schedules) {
            if (schedule.getResumeId() == null) {
                continue;
            }
            InterviewSchedule existing = scheduleByResumeId.get(schedule.getResumeId());
            if (existing == null || schedule.getScheduleId() > existing.getScheduleId()) {
                scheduleByResumeId.put(schedule.getResumeId(), schedule);
            }
        }

        List<Integer> scheduleIds = scheduleByResumeId.values().stream()
                .map(InterviewSchedule::getScheduleId)
                .toList();
        Map<Integer, InterviewResult> resultByScheduleId = new HashMap<>();
        if (!scheduleIds.isEmpty()) {
            interviewResultService.lambdaQuery()
                    .in(InterviewResult::getScheduleId, scheduleIds)
                    .list()
                    .forEach(r -> resultByScheduleId.put(r.getScheduleId(), r));
        }

        Set<String> decisionNames = new HashSet<>();
        for (FeishuBitableRecord record : records) {
            String name = normalizeDecisionMakerName(record.decisionMakerName());
            if (StringUtils.hasText(name)) {
                decisionNames.add(name);
            }
        }
        Map<String, Integer> decisionByUserName = new HashMap<>();
        if (!decisionNames.isEmpty()) {
            List<User> decisionUsers = userService.lambdaQuery()
                    .in(User::getName, decisionNames)
                    .eq(User::getIsDeleted, 0)
                    .list();
            for (User user : decisionUsers) {
                if (StringUtils.hasText(user.getName())) {
                    decisionByUserName.putIfAbsent(user.getName().trim(), user.getUserId());
                }
            }
        }

        return new PullPreloadContext(
                deptByName,
                usersByName,
                userIdsInCycle,
                resumeByUserId,
                scheduleByResumeId,
                resultByScheduleId,
                decisionByUserName,
                defaultOperatorUserId);
    }

    private static String normalizeDecisionMakerName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.trim();
        if (name.startsWith("@")) {
            name = name.substring(1).trim();
        }
        return name;
    }

    private Integer resolveDecisionByUserId(
            String decisionMakerName, Integer defaultOperatorUserId, PullPreloadContext preload) {
        String name = normalizeDecisionMakerName(decisionMakerName);
        if (!StringUtils.hasText(name)) {
            return defaultOperatorUserId;
        }
        Integer userId = preload.decisionByUserName.get(name);
        return userId != null ? userId : defaultOperatorUserId;
    }

    private Map<String, Department> loadDepartmentByName() {
        List<Department> list = departmentService.lambdaQuery()
                .eq(Department::getStatus, 1)
                .list();
        Map<String, Department> map = new HashMap<>();
        for (Department d : list) {
            if (StringUtils.hasText(d.getDeptName())) {
                map.put(normalizeDeptKey(d.getDeptName()), d);
            }
        }
        return map;
    }

    private static String normalizeDeptKey(String name) {
        return name.trim().toLowerCase();
    }

    private Department resolveDepartment(Map<String, Department> deptByName, String deptName) {
        Department exact = deptByName.get(normalizeDeptKey(deptName));
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Department> e : deptByName.entrySet()) {
            if (deptName.equalsIgnoreCase(e.getValue().getDeptName())) {
                return e.getValue();
            }
        }
        return null;
    }

    private User resolveUserInCycle(String name, PullPreloadContext preload) {
        List<User> candidates = preload.usersByName.getOrDefault(name, List.of());
        if (candidates.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND, "未找到姓名对应的用户: " + name);
        }
        List<User> inCycle = candidates.stream()
                .filter(u -> preload.userIdsInCycle.contains(u.getUserId()))
                .toList();
        if (inCycle.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND,
                    "该用户在当前招新周期无简历: " + name);
        }
        if (inCycle.size() > 1) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT,
                    "当前周期存在重名用户，无法自动匹配: " + name);
        }
        return inCycle.get(0);
    }

    private InterviewResult upsertInterviewResult(
            User user,
            Integer deptId,
            int decision,
            Integer decisionByUserId,
            PullPreloadContext preload) {
        Resume resume = preload.resumeByUserId.get(user.getUserId());
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND,
                    "该用户在当前招新周期无简历");
        }

        InterviewSchedule schedule = preload.scheduleByResumeId.get(resume.getResumeId());
        if (schedule == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_BOOKING_NOT_FOUND,
                    "该用户在本周期无有效面试安排，无法写入面试结果");
        }

        InterviewResult existing = preload.resultByScheduleId.get(schedule.getScheduleId());
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setDecision(decision);
            if (deptId != null) {
                existing.setAssignedDeptId(deptId);
            }
            if (decisionByUserId != null) {
                existing.setDecisionBy(decisionByUserId);
            }
            existing.setDecisionAt(now);
            interviewResultService.updateById(existing);
            return existing;
        }

        InterviewResult created = new InterviewResult()
                .setScheduleId(schedule.getScheduleId())
                .setUserId(user.getUserId())
                .setAssignedDeptId(deptId)
                .setDecision(decision)
                .setDecisionBy(decisionByUserId)
                .setDecisionAt(now);
        interviewResultService.save(created);
        preload.resultByScheduleId.put(schedule.getScheduleId(), created);
        return created;
    }

    private record PullPreloadContext(
            Map<String, Department> deptByName,
            Map<String, List<User>> usersByName,
            Set<Integer> userIdsInCycle,
            Map<Integer, Resume> resumeByUserId,
            Map<Integer, InterviewSchedule> scheduleByResumeId,
            Map<Integer, InterviewResult> resultByScheduleId,
            Map<String, Integer> decisionByUserName,
            Integer defaultOperatorUserId) {
    }
}
