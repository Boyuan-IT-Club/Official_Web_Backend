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

    @Override
    public InterviewResult update(Integer resultId, InterviewResultSaveDTO interviewResult) {
        Integer userId = currentUserId();
        if (userId != null) {
            interviewResult.setDecisionBy(userId);
        }

        //构建更新对象
        LambdaUpdateWrapper<InterviewResult> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper
                .eq(InterviewResult::getResultId, resultId)
                .set(interviewResult.getDecision()!=null, InterviewResult::getDecision, interviewResult.getDecision())
                .set(interviewResult.getAssignedDeptId()!=null, InterviewResult::getAssignedDeptId, interviewResult.getAssignedDeptId())
                .set(InterviewResult::getDecisionBy, interviewResult.getDecisionBy())
                // decision_at 没有数据库层的自动更新，此前一直没人写它，导致管理端「决定时间」永远是空
                .set(interviewResult.getDecision()!=null, InterviewResult::getDecisionAt, LocalDateTime.now());
        this.update(updateWrapper);
        return this.getById(resultId);

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
        if (decision == 1 && request.getAssignedDeptId() == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "批量录取时必须指定录取部门");
        }

        // 结果的周期挂在 interview_schedule 上，先过滤出确实属于本周期的 ID，
        // 夹带的别届 ID 计入 skipped 而不是跟着一起被改。
        // onlyPending 时再收窄到「仍为待定、不是初筛未通过、且不在预录取名单里」的行：
        // 防止按一份旧列表把别人刚录取的人改掉，也不动部门已打算录取（草稿里）的人
        boolean onlyPending = Boolean.TRUE.equals(request.getOnlyPending());
        List<Integer> requested = request.getResultIds();
        List<Integer> valid = onlyPending
                ? baseMapper.selectRejectablePendingResultIdsInCycle(request.getCycleId(), requested)
                : baseMapper.selectResultIdsInCycle(request.getCycleId(), requested);
        Set<Integer> validSet = new HashSet<>(valid);
        List<Integer> skipped = new ArrayList<>();
        for (Integer id : requested) {
            if (!validSet.contains(id)) {
                skipped.add(id);
            }
        }
        if (valid.isEmpty()) {
            log.warn("批量录取无有效目标，cycleId={}，请求 {} 条全部不属于该周期", request.getCycleId(), requested.size());
            return new BatchDecisionResponseDTO(0, skipped);
        }

        LambdaUpdateWrapper<InterviewResult> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(InterviewResult::getResultId, valid)
                .set(InterviewResult::getDecision, decision)
                // 标记未通过时清空录取部门，否则会残留上一次误录取的部门
                .set(InterviewResult::getAssignedDeptId, decision == 1 ? request.getAssignedDeptId() : null)
                .set(InterviewResult::getDecisionBy, currentUserId())
                .set(InterviewResult::getDecisionAt, LocalDateTime.now());
        if (onlyPending) {
            // 上面筛完到这里写入之间若有人抢先改了某一行、或把人加进了预录取名单，这一行也不动
            wrapper.eq(InterviewResult::getDecision, 0)
                    .notExists("SELECT 1 FROM pre_admission_draft pad WHERE pad.result_id = interview_result.result_id");
        }
        int updated = baseMapper.update(null, wrapper);

        log.info("批量录取完成，cycleId={}，decision={}，deptId={}，onlyPending={}，更新 {} 条，跳过 {} 条",
                request.getCycleId(), decision, request.getAssignedDeptId(), onlyPending, updated, skipped.size());
        return new BatchDecisionResponseDTO(updated, skipped);
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
