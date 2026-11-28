package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.InterviewRescheduleRequest;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewRescheduleRequestMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class RescheduleDecisionService {
    private final InterviewRescheduleRequestMapper rescheduleMapper;
    private final InterviewScheduleMapper interviewScheduleMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final IUserService userService;

    public static class InvalidDecision extends RuntimeException {
        InvalidDecision(String message) {
            super(message);
        }
    }

    /** 锁定申请后再锁排程所在周期，审批、排程和座位在同一事务提交。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InterviewRescheduleRequest handle(Integer requestId, Integer status, String adminNote, String handlerUsername) {
        if (status == null || (status != InterviewRescheduleRequest.STATUS_APPROVED
                && status != InterviewRescheduleRequest.STATUS_REJECTED)) {
            throw new InvalidDecision("status 仅支持 1(同意)/2(拒绝)");
        }
        InterviewRescheduleRequest req = rescheduleMapper.selectOne(new LambdaQueryWrapper<InterviewRescheduleRequest>()
                .eq(InterviewRescheduleRequest::getRequestId, requestId).last("FOR UPDATE"));
        if (req == null) {
            throw new InvalidDecision("申请不存在");
        }
        if (req.getStatus() != null && req.getStatus() != InterviewRescheduleRequest.STATUS_PENDING) {
            throw new InvalidDecision("该申请已处理");
        }
        User handler = userService.getUserByUsername(handlerUsername);
        req.setStatus(status)
                .setAdminNote(adminNote)
                .setHandledBy(handler.getUserId())
                .setHandledAt(LocalDateTime.now());
        rescheduleMapper.updateById(req);

        if (status == InterviewRescheduleRequest.STATUS_APPROVED && req.getScheduleId() != null) {
            interviewSessionMapper.lockCycleForSchedule(req.getScheduleId());
            InterviewSchedule schedule = interviewScheduleMapper.selectById(req.getScheduleId());
            boolean toOnline = Integer.valueOf(InterviewRescheduleRequest.TYPE_TO_ONLINE)
                    .equals(req.getRequestType());
            // 已取消的安排仍可转线上，座位在此前取消时已经归还。
            if (schedule != null && toOnline) {
                approveToOnline(requestId, schedule);
            } else if (schedule != null && Integer.valueOf(1).equals(schedule.getStatus())) {
                schedule.setStatus(2); // 已取消，等待人工重排
                interviewScheduleMapper.updateById(schedule);
                if (schedule.getSessionId() != null) interviewSessionMapper.releaseOne(schedule.getSessionId());
                log.info("改期申请{}已同意，原面试安排{}已取消待重排", requestId, schedule.getScheduleId());
            }
        }

        log.info("管理员{}处理改期申请{}，status={}", handler.getUsername(), requestId, status);
        return req;
    }

    private void approveToOnline(Integer requestId, InterviewSchedule schedule) {
        Integer oldSessionId = schedule.getSessionId();
        boolean wasActive = Integer.valueOf(1).equals(schedule.getStatus());
        schedule.setInterviewMode(1)
                .setStatus(1);       // 已取消的行在这里重新生效：转线上不需要再排进任何场次
        interviewScheduleMapper.update(null, new LambdaUpdateWrapper<InterviewSchedule>()
                .set(InterviewSchedule::getInterviewMode, 1)
                .set(InterviewSchedule::getStatus, 1)
                .set(InterviewSchedule::getSessionId, null)
                .set(InterviewSchedule::getNotifStatus, 0)
                .eq(InterviewSchedule::getScheduleId, schedule.getScheduleId()));
        schedule.setSessionId(null);
        // 只有原本还占着座位的才归还；已取消的那行在取消时已经还过了
        if (wasActive && oldSessionId != null) {
            interviewSessionMapper.releaseOne(oldSessionId);
        }
        log.info("改期申请{}已同意为线上面试，安排{}已转线上并释放场次{}",
                requestId, schedule.getScheduleId(), oldSessionId);
    }
}
