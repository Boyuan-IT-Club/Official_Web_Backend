package club.boyuan.official.domain.interview.service.impl;

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
import org.springframework.util.StringUtils;

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
        // 夹带的别届 ID 计入 skipped 而不是跟着一起被改
        List<Integer> requested = request.getResultIds();
        List<Integer> valid = baseMapper.selectResultIdsInCycle(request.getCycleId(), requested);
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
        this.update(wrapper);

        log.info("批量录取完成，cycleId={}，decision={}，deptId={}，更新 {} 条，跳过 {} 条",
                request.getCycleId(), decision, request.getAssignedDeptId(), valid.size(), skipped.size());
        return new BatchDecisionResponseDTO(valid.size(), skipped);
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
        List<InterviewSchedule> schedules = interviewScheduleMapper.selectList(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .eq(InterviewSchedule::getStatus, 1));
        if (schedules.isEmpty()) {
            return 0;
        }

        Set<Integer> scheduleIds = schedules.stream()
                .map(InterviewSchedule::getScheduleId)
                .collect(Collectors.toSet());
        Set<Integer> seeded = baseMapper.selectList(
                        new LambdaQueryWrapper<InterviewResult>()
                                .in(InterviewResult::getScheduleId, scheduleIds))
                .stream()
                .map(InterviewResult::getScheduleId)
                .collect(Collectors.toSet());

        int created = 0;
        for (InterviewSchedule schedule : schedules) {
            if (seeded.contains(schedule.getScheduleId())) {
                continue;   // 已有结果行（无论站内建的还是飞书拉的）一律不动
            }
            Integer userId = schedule.getUserId();
            if (userId == null && schedule.getResumeId() != null) {
                // user_id 是后补列，历史安排可能为空，用简历兜底
                Resume resume = resumeMapper.selectById(schedule.getResumeId());
                userId = resume != null ? resume.getUserId() : null;
            }
            if (userId == null) {
                log.warn("安排 {} 无法解析候选人，跳过生成结果行", schedule.getScheduleId());
                continue;
            }
            InterviewResult row = new InterviewResult()
                    .setScheduleId(schedule.getScheduleId())
                    .setUserId(userId)
                    .setDecision(0);   // 0=待定，等管理员在「结果与通知」里定
            baseMapper.insert(row);
            created++;
        }
        log.info("周期 {} 从面试安排生成结果名单：新建 {} 行，跳过已存在 {} 行",
                cycleId, created, schedules.size() - created);
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
            if (!StringUtils.hasText(customMessage)
                    && (decision == null || (decision != 1 && decision != 2))) {
                log.warn("结果 decision={} 无自定义正文且不支持自动邮件，resultId={}",
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
