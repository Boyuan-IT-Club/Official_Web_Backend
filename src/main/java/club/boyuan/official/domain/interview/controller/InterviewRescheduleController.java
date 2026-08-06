package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.InterviewRescheduleRequest;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewRescheduleRequestMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试改期申请：
 * 学生对已分配的面试提出改期（附原因与期望时间窗），
 * 管理员审核（同意后在「分配与调剂」中人工重排，或拒绝并备注）。
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/reschedule")
@AllArgsConstructor
public class InterviewRescheduleController {

    private final IUserService userService;

    private final IResumeService resumeService;

    private final InterviewRescheduleRequestMapper rescheduleMapper;

    private final InterviewScheduleMapper interviewScheduleMapper;

    /**
     * 学生提交改期申请。要求本周期已有面试排期；同一排期存在待处理申请时不可重复提交。
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewRescheduleRequest>> submit(
            @RequestBody Map<String, Object> body) {
        Integer cycleId = body.get("cycleId") == null ? null : Integer.valueOf(String.valueOf(body.get("cycleId")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason")).trim();
        String preferredSlots = body.get("preferredTimeSlotIds") == null
                ? null : String.valueOf(body.get("preferredTimeSlotIds"));
        if (cycleId == null || reason == null || reason.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "cycleId 与 reason 不能为空"));
        }
        if (reason.length() > 500) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "原因不能超过 500 字"));
        }

        User currentUser = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "本周期尚未投递简历"));
        }
        InterviewSchedule schedule = interviewScheduleMapper.selectOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resume.getResumeId())
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .orderByDesc(InterviewSchedule::getScheduleId)
                        .last("LIMIT 1"));
        if (schedule == null) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "尚未分配面试，无需改期"));
        }
        Long pending = rescheduleMapper.selectCount(new LambdaQueryWrapper<InterviewRescheduleRequest>()
                .eq(InterviewRescheduleRequest::getScheduleId, schedule.getScheduleId())
                .eq(InterviewRescheduleRequest::getStatus, InterviewRescheduleRequest.STATUS_PENDING));
        if (pending != null && pending > 0) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "已有待处理的改期申请，请耐心等待"));
        }

        InterviewRescheduleRequest req = new InterviewRescheduleRequest()
                .setScheduleId(schedule.getScheduleId())
                .setResumeId(resume.getResumeId())
                .setUserId(currentUser.getUserId())
                .setCycleId(cycleId)
                .setReason(reason)
                .setPreferredTimeSlotIds(preferredSlots)
                .setStatus(InterviewRescheduleRequest.STATUS_PENDING);
        rescheduleMapper.insert(req);
        log.info("用户{}提交改期申请，scheduleId={}, requestId={}",
                currentUser.getUsername(), schedule.getScheduleId(), req.getRequestId());
        return ResponseEntity.ok(ResponseMessage.success(req));
    }

    /**
     * 学生查询本人在指定周期的最新改期申请；没有时 data 为 null。
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewRescheduleRequest>> my(@RequestParam Integer cycleId) {
        User currentUser = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        InterviewRescheduleRequest req = rescheduleMapper.selectOne(
                new LambdaQueryWrapper<InterviewRescheduleRequest>()
                        .eq(InterviewRescheduleRequest::getUserId, currentUser.getUserId())
                        .eq(InterviewRescheduleRequest::getCycleId, cycleId)
                        .orderByDesc(InterviewRescheduleRequest::getRequestId)
                        .last("LIMIT 1"));
        return ResponseEntity.ok(ResponseMessage.success(req));
    }

    /**
     * 管理员按周期查询改期申请列表（可按状态过滤，默认全部）。
     */
    @GetMapping("/admin/list")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<List<InterviewRescheduleRequest>>> adminList(
            @RequestParam Integer cycleId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<InterviewRescheduleRequest> qw = new LambdaQueryWrapper<InterviewRescheduleRequest>()
                .eq(InterviewRescheduleRequest::getCycleId, cycleId)
                .orderByAsc(InterviewRescheduleRequest::getStatus)
                .orderByDesc(InterviewRescheduleRequest::getRequestId);
        if (status != null) {
            qw.eq(InterviewRescheduleRequest::getStatus, status);
        }
        return ResponseEntity.ok(ResponseMessage.success(rescheduleMapper.selectList(qw)));
    }

    /**
     * 管理员处理改期申请：status=1 同意（随后在「分配与调剂」人工重排）；status=2 拒绝。
     */
    @PutMapping("/admin/{requestId}/handle")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<InterviewRescheduleRequest>> handle(
            @PathVariable Integer requestId,
            @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status")));
        String adminNote = body.get("adminNote") == null ? null : String.valueOf(body.get("adminNote"));
        if (status == null || (status != InterviewRescheduleRequest.STATUS_APPROVED
                && status != InterviewRescheduleRequest.STATUS_REJECTED)) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "status 仅支持 1(同意)/2(拒绝)"));
        }
        InterviewRescheduleRequest req = rescheduleMapper.selectById(requestId);
        if (req == null) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "申请不存在"));
        }
        if (req.getStatus() != null && req.getStatus() != InterviewRescheduleRequest.STATUS_PENDING) {
            return ResponseEntity.badRequest().body(ResponseMessage.error(400, "该申请已处理"));
        }
        User handler = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        req.setStatus(status)
                .setAdminNote(adminNote)
                .setHandledBy(handler.getUserId())
                .setHandledAt(LocalDateTime.now());
        rescheduleMapper.updateById(req);
        log.info("管理员{}处理改期申请{}，status={}", handler.getUsername(), requestId, status);
        return ResponseEntity.ok(ResponseMessage.success(req));
    }
}
