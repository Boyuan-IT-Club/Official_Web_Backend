package club.boyuan.official.domain.interview.service.impl;

import java.util.Map;
import java.util.stream.Collectors;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.BatchDecisionRequestDTO;
import club.boyuan.official.domain.interview.dto.BatchDecisionResponseDTO;
import club.boyuan.official.domain.interview.dto.InterviewResultResponseDTO;
import club.boyuan.official.domain.interview.dto.InterviewResultSaveDTO;
import club.boyuan.official.domain.interview.dto.SendNotificationsRequestDTO;
import club.boyuan.official.domain.interview.dto.SendNotificationsResponseDTO;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.entity.InterviewResultChange;
import club.boyuan.official.persistence.entity.PreAdmissionDraft;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.mapper.InterviewResultChangeMapper;
import club.boyuan.official.persistence.mapper.PreAdmissionDraftMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import org.springframework.util.StringUtils;
import java.util.Objects;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.interview.service.InterviewNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 面试结果表 服务实现类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Service
@Slf4j
public class InterviewResultServiceImpl extends ServiceImpl<InterviewResultMapper, InterviewResult> implements IInterviewResultService {

    /** 简历状态：初筛未通过（与 ResumeServiceImpl.STATUS_SCREEN_REJECTED 一致） */
    private static final int RESUME_STATUS_SCREEN_REJECTED = 5;

    @Autowired
    private IUserService userService;
    @Autowired
    private InterviewScheduleMapper interviewScheduleMapper;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private InterviewNotificationService interviewNotificationService;
    @Autowired
    private PreAdmissionDraftMapper preAdmissionDraftMapper;
    @Autowired
    private InterviewResultChangeMapper changeMapper;
    @Autowired
    private DepartmentMapper departmentMapper;
    @Override
    public SendNotificationsResponseDTO sendNotifications(SendNotificationsRequestDTO requestDTO) {
        List<Integer> resultIds = requestDTO.getResultIds();
        //发送通知的类型（暂时只有邮箱，未开通sms短信服务）
        String notificationType = requestDTO.getNotificationType();
        String customMessage = requestDTO.getCustomMessage();

        int sendCount=0;
        int failedCount=0;
        ArrayList<Integer> failedId = new ArrayList<>();

        for(Integer resultId:resultIds){
            try {
                //根据resultId查询面试结果
                InterviewResult interviewResult = this.getById(resultId);
                if(interviewResult==null){
                    failedCount++;
                    failedId.add(-1);
                    throw new RuntimeException("面试结果不存在");
                }
                //获取用户信息
                User user = userService.getById(interviewResult.getUserId());
                if(user==null){
                    failedCount++;
                    failedId.add(-1);
                    throw new RuntimeException("用户不存在");
                }
                //根据通知类型发送通知
                Boolean sent = false;
                switch (notificationType.toLowerCase()){
                    case "email":
                        sent = sendEmailNotification(interviewResult, customMessage);
                        break;
                    case "sms":
                        sent = sendSmsNotifaction(user,interviewResult,customMessage);
                        break;
                    default:
                        throw new RuntimeException("不支持的通知类型");
                }
                if(sent){
                    sendCount++;
                    // 通知留痕：只动 notified_at 一列。没有这行时发送完全无痕 ——
                    // 管理端看不出谁通知过，重复点发送会对同一批人原样重发。
                    this.update(new LambdaUpdateWrapper<InterviewResult>()
                            .eq(InterviewResult::getResultId, resultId)
                            .set(InterviewResult::getNotifiedAt, LocalDateTime.now()));
                }else{
                    failedCount++;
                }
            }catch (Exception e){
                log.error("发送通知失败，resultId: {}", resultId, e);
                failedCount++;
                failedId.add(resultId);
            }
        }
        SendNotificationsResponseDTO responseDTO = new SendNotificationsResponseDTO();
        responseDTO.setSentCount(sendCount);
        responseDTO.setFailedCount(failedCount);
        responseDTO.setFailedId(failedId);
        return responseDTO;

    }

    @Override
    public InterviewResultResponseDTO list(Integer cycleId, String name, String decision, String department, Integer page, Integer size) {

        //构建查询对象
        Page<InterviewResult> pageInfo = new Page<>(page, size);
        Page<InterviewResult> resultPage = baseMapper.selectResultPage(pageInfo, cycleId, name, decision, department);
        InterviewResultResponseDTO responseDTO = new InterviewResultResponseDTO();
        responseDTO.setTotal(resultPage.getTotal());
        responseDTO.setInterviewResults(resultPage.getRecords());
        return responseDTO;

    }

    /**
     * 单行录入 / 更正结果。
     *
     * 「已定即锁」的唯一出口：结果已是通过/未通过时再改就是更正，必须填原因；
     * 每次变动都写 interview_result_change。规则：
     * - 行不存在 → 404；在预录取名单里 → 409（请在预录取页处理，免得两边各改各的）；
     * - 只能定为 1/2（DTO 校验），不能改回待定；
     * - 录取必须选启用中的部门；未通过一律清空部门（此前会残留上一次误录取的部门）；
     * - 乐观更新：按读到的旧结论、旧部门作条件，期间被别人改过就 409，不静默覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewResult update(Integer resultId, InterviewResultSaveDTO dto) {
        InterviewResult current = baseMapper.selectById(resultId);
        if (current == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_RESULT_NOT_FOUND);
        }
        if (preAdmissionDraftMapper.exists(new LambdaQueryWrapper<PreAdmissionDraft>()
                .eq(PreAdmissionDraft::getResultId, resultId))) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_RESULT_IN_PRE_ADMISSION);
        }

        Integer to = dto.getDecision();
        Integer toDept = Integer.valueOf(1).equals(to)
                ? requireActiveDept(dto.getAssignedDeptId(), "录取时必须选择录取部门")
                : null;
        Integer from = current.getDecision();
        Integer fromDept = current.getAssignedDeptId();
        if (Objects.equals(from, to) && Objects.equals(fromDept, toDept)) {
            return current;   // 没有变化：不写库、不留痕
        }

        boolean decided = isDecided(from);
        String reason = dto.getReason() == null ? null : dto.getReason().trim();
        if (decided && !StringUtils.hasText(reason)) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_RESULT_DECIDED);
        }

        Integer operator = currentUserId();
        LambdaUpdateWrapper<InterviewResult> wrapper = new LambdaUpdateWrapper<InterviewResult>()
                .eq(InterviewResult::getResultId, resultId)
                .eq(InterviewResult::getDecision, from)
                .set(InterviewResult::getDecision, to)
                .set(InterviewResult::getAssignedDeptId, toDept)
                .set(InterviewResult::getDecisionBy, operator)
                // decision_at 没有数据库层的自动更新，此前一直没人写它，导致管理端「决定时间」永远是空
                .set(InterviewResult::getDecisionAt, LocalDateTime.now());
        if (fromDept == null) {
            wrapper.isNull(InterviewResult::getAssignedDeptId);
        } else {
            wrapper.eq(InterviewResult::getAssignedDeptId, fromDept);
        }
        if (baseMapper.update(null, wrapper) == 0) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "这位同学的结果刚被别人改过，请刷新后重试");
        }

        logChange(current, decided ? InterviewResultChange.ACTION_CORRECT : InterviewResultChange.ACTION_DECIDE,
                to, toDept, reason, operator);
        log.info("{}结果 resultId={}：{}/{} → {}/{}，原因={}", decided ? "更正" : "录入",
                resultId, from, fromDept, to, toDept, reason);
        return baseMapper.selectById(resultId);
    }

    /**
     * 批量录取 / 批量标记未通过。
     *
     * 一条 UPDATE ... IN (...) 落库，同一批要么都改要么都不改，不做 N 次单条更新——
     * 逐条更新在中途失败时会留下一半录取、一半待录入的半成品状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchDecisionResponseDTO batchDecision(BatchDecisionRequestDTO request) {
        // decision 的取值范围由 DTO 上的 @Min/@Max 声明式校验拦住（返回 400），这里只管业务规则
        Integer decision = request.getDecision();
        Integer deptId = Integer.valueOf(1).equals(decision)
                ? requireActiveDept(request.getAssignedDeptId(), "批量录取时必须指定录取部门")
                : null;
        Integer cycleId = request.getCycleId();
        List<Integer> requested = request.getResultIds().stream().filter(Objects::nonNull).distinct().toList();

        // 已定即锁：批量入口一律只改仍未决定、不是初筛未通过、且不在预录取名单里的行，
        // 并在事务里锁住它们。onlyPending 参数已弃用，传不传都按这条规则走——
        // 原先手动勾选后批量标记会把已录取、甚至已发过录取信的人直接改成未通过。
        List<InterviewResult> targets = baseMapper.lockBatchTargets(cycleId, requested);
        Set<Integer> targetIds = targets.stream().map(InterviewResult::getResultId).collect(Collectors.toSet());
        List<Integer> skipped = requested.stream().filter(id -> !targetIds.contains(id)).toList();

        // 说清楚为什么没改：已有结果的、在预录取名单里的分开计数（别届/不存在的不计入这两类）
        int skippedDecided = 0;
        int skippedInPreAdmission = 0;
        if (!skipped.isEmpty()) {
            List<Integer> inCycle = baseMapper.selectResultIdsInCycle(cycleId, skipped);
            if (!inCycle.isEmpty()) {
                skippedDecided = (int) baseMapper.selectBatchIds(inCycle).stream()
                        .filter(r -> isDecided(r.getDecision())).count();
                skippedInPreAdmission = Math.toIntExact(preAdmissionDraftMapper.selectCount(
                        new LambdaQueryWrapper<PreAdmissionDraft>().in(PreAdmissionDraft::getResultId, inCycle)));
            }
        }
        if (targets.isEmpty()) {
            log.warn("批量录入无可改目标，cycleId={}，请求 {} 条（已定 {}，在预录取 {}）",
                    cycleId, requested.size(), skippedDecided, skippedInPreAdmission);
            return new BatchDecisionResponseDTO(0, skipped, skippedDecided, skippedInPreAdmission);
        }

        Integer operator = currentUserId();
        LambdaUpdateWrapper<InterviewResult> wrapper = new LambdaUpdateWrapper<InterviewResult>()
                .in(InterviewResult::getResultId, targetIds)
                // 行已锁住，这两个条件只是兜底：万一不在事务里被调用，也不至于覆盖已定的结果
                .in(InterviewResult::getDecision, 0, 3)
                .set(InterviewResult::getDecision, decision)
                // 标记未通过时清空录取部门，否则会残留上一次误录取的部门
                .set(InterviewResult::getAssignedDeptId, deptId)
                .set(InterviewResult::getDecisionBy, operator)
                .set(InterviewResult::getDecisionAt, LocalDateTime.now());
        int updated = baseMapper.update(null, wrapper);
        targets.forEach(t -> logChange(t, InterviewResultChange.ACTION_BATCH, decision, deptId, null, operator));

        log.info("批量录入完成，cycleId={}，decision={}，deptId={}，更新 {} 条，跳过 {} 条（已定 {}，在预录取 {}）",
                cycleId, decision, deptId, updated, skipped.size(), skippedDecided, skippedInPreAdmission);
        return new BatchDecisionResponseDTO(updated, skipped, skippedDecided, skippedInPreAdmission);
    }

    /**
     * 从 Spring Security 上下文取当前用户ID，用于记录决定人。
     */
    @Override
    @Transactional
    public int seedFromSchedules(Integer cycleId) {
        if (cycleId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }

        // 已有结果行的简历一律不动（无论站内建的还是飞书拉的）
        Set<Integer> seededResumes = baseMapper.selectList(
                        new LambdaQueryWrapper<InterviewResult>().eq(InterviewResult::getCycleId, cycleId))
                .stream()
                .map(InterviewResult::getResumeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<InterviewSchedule> schedules = interviewScheduleMapper.selectList(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, 1));
        Map<Integer, InterviewSchedule> scheduleByResume = new java.util.HashMap<>();
        for (InterviewSchedule sc : schedules) {
            if (sc.getResumeId() != null) {
                scheduleByResume.putIfAbsent(sc.getResumeId(), sc);
            }
        }

        // 本周期所有已提交的简历，而不只是「有面试安排的」——
        // 选了「不能参加线下面试」的同学不进排期，因人数原因没排上的也一样，
        // 只看 schedule 会让他们在「结果与通知」里彻底消失，管理员无从录取或婉拒。
        //
        // 但初筛未通过的不算：他们在简历阶段就被刷掉了，不参与分配、拿不到面试，
        // 未通过通知也有单独入口。生成进来只会让人在录取名单里看到一批没面试过的人，
        // 一旦被批量婉拒还会再收到一封未录取邮件。
        List<Resume> submitted = resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getCycleId, cycleId)
                        .ge(Resume::getStatus, 2)
                        .ne(Resume::getStatus, RESUME_STATUS_SCREEN_REJECTED));

        int created = 0;
        for (Resume resume : submitted) {
            if (resume.getResumeId() == null || seededResumes.contains(resume.getResumeId())) {
                continue;
            }
            InterviewSchedule sc = scheduleByResume.get(resume.getResumeId());
            Integer userId = resume.getUserId();
            if (userId == null && sc != null) {
                userId = sc.getUserId();
            }
            if (userId == null) {
                log.warn("简历 {} 无法解析候选人，跳过生成结果行", resume.getResumeId());
                continue;
            }
            InterviewResult row = new InterviewResult()
                    .setScheduleId(sc == null ? null : sc.getScheduleId())   // 无安排时留空（V34 起可空）
                    .setResumeId(resume.getResumeId())
                    .setCycleId(cycleId)
                    .setUserId(userId)
                    .setDecision(0);   // 0=待定，等管理员在「结果与通知」里定
            baseMapper.insert(row);
            created++;
        }
        log.info("周期 {} 生成结果名单：新建 {} 行（其中无面试安排 {} 行），跳过已存在 {} 行",
                cycleId, created,
                submitted.stream().filter(r -> r.getResumeId() != null
                        && !seededResumes.contains(r.getResumeId())
                        && !scheduleByResume.containsKey(r.getResumeId())).count(),
                seededResumes.size());
        return created;
    }

    /** 1 通过 / 2 未通过 才算「已定」；0 待定、3 待调剂都还没决定 */
    private static boolean isDecided(Integer decision) {
        return Integer.valueOf(1).equals(decision) || Integer.valueOf(2).equals(decision);
    }

    /** 录取部门必须存在且启用：停用的部门录进去，学生端和通知里会显示一个已经没有的部门 */
    private Integer requireActiveDept(Integer deptId, String missingMessage) {
        if (deptId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, missingMessage);
        }
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null || !Integer.valueOf(1).equals(dept.getStatus())) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "录取部门不存在或已停用");
        }
        return deptId;
    }

    /** 写一条结果变更留痕（V54）。改前的结论/部门/是否已通知取自传入的当前行 */
    private void logChange(InterviewResult before, String action, Integer toDecision, Integer toDept,
                           String reason, Integer operator) {
        changeMapper.insert(new InterviewResultChange()
                .setResultId(before.getResultId())
                .setCycleId(before.getCycleId())
                .setAction(action)
                .setFromDecision(before.getDecision())
                .setFromDeptId(before.getAssignedDeptId())
                .setToDecision(toDecision)
                .setToDeptId(toDept)
                .setWasNotified(before.getNotifiedAt() != null ? 1 : 0)
                .setReason(reason)
                .setOperatorId(operator));
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof String) {
            username = (String) principal;
        } else if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        }
        if (username == null) {
            return null;
        }
        User user = userService.getUserByUsername(username);
        return user != null ? user.getUserId() : null;
    }

    //sms尚未开通，短信通知功能留白
    private Boolean sendSmsNotifaction(User user, InterviewResult interviewResult, String customMessage) {
        return false;
    }

    private Boolean sendEmailNotification(InterviewResult interviewResult, String customMessage) {
        try {
            Integer decision = interviewResult.getDecision();
            // 自定义正文也不能绕过最终决定闸门，否则管理员可能在草稿讨论期
            // 误选待定结果并提前把录取信息发给学生。
            if (decision == null || (decision != 1 && decision != 2)) {
                log.warn("结果 decision={} 尚未定稿，拒绝发送结果通知，resultId={}",
                        decision, interviewResult.getResultId());
                return false;
            }
            interviewNotificationService.enqueueResultNotification(interviewResult.getResultId(), customMessage);
            return true;
        } catch (Exception e) {
            log.error("投递结果通知失败 resultId={}", interviewResult.getResultId(), e);
            return false;
        }
    }
}
