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
import club.boyuan.official.domain.interview.dto.RescheduleRequestAdminDTO;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final InterviewTimeSlotMapper interviewTimeSlotMapper;

    private final InterviewSessionMapper interviewSessionMapper;

    private final UserMapper userMapper;

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
    @PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<RescheduleRequestAdminDTO>>> adminList(
            @RequestParam Integer cycleId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<InterviewRescheduleRequest> qw = new LambdaQueryWrapper<InterviewRescheduleRequest>()
                .eq(InterviewRescheduleRequest::getCycleId, cycleId)
                .orderByAsc(InterviewRescheduleRequest::getStatus)
                .orderByDesc(InterviewRescheduleRequest::getRequestId);
        if (status != null) {
            qw.eq(InterviewRescheduleRequest::getStatus, status);
        }
        return ResponseEntity.ok(ResponseMessage.success(toAdminView(rescheduleMapper.selectList(qw))));
    }

    private static final DateTimeFormatter SLOT_DATE = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter SLOT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 把实体翻译成管理员看得懂的东西。
     * 批量取人、取时间窗、取安排，避免一行一查。
     */
    private List<RescheduleRequestAdminDTO> toAdminView(List<InterviewRescheduleRequest> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Integer, User> users = batchUsers(rows);
        Map<Integer, InterviewSchedule> schedules = batchSchedules(rows);
        Map<Integer, InterviewTimeSlot> slots = batchSlots(rows);
        Map<Integer, String> locations = batchLocations(schedules.values());

        List<RescheduleRequestAdminDTO> out = new ArrayList<>(rows.size());
        for (InterviewRescheduleRequest r : rows) {
            User u = r.getUserId() == null ? null : users.get(r.getUserId());
            InterviewSchedule sc = r.getScheduleId() == null ? null : schedules.get(r.getScheduleId());
            out.add(RescheduleRequestAdminDTO.builder()
                    .requestId(r.getRequestId())
                    .name(u == null ? null : u.getName())
                    .studentId(u == null ? null : u.getUsername())
                    .reason(r.getReason())
                    .currentInterviewTime(sc == null ? null : sc.getInterviewTime())
                    .currentLocation(sc == null || sc.getSessionId() == null
                            ? null : locations.get(sc.getSessionId()))
                    .preferredSlots(resolveSlots(r.getPreferredTimeSlotIds(), slots))
                    .submittedAt(r.getCreatedAt())
                    .status(r.getStatus())
                    .adminNote(r.getAdminNote())
                    .handledAt(r.getHandledAt())
                    // 同意了、但那条安排还停在「已取消」上，就是还没重排
                    .awaitingReassign(Integer.valueOf(InterviewRescheduleRequest.STATUS_APPROVED).equals(r.getStatus())
                            && sc != null && !Integer.valueOf(1).equals(sc.getStatus()))
                    .build());
        }
        return out;
    }

    /** "11,12,13" → 三个「10-11 周六上午 09:00-11:00」 */
    private List<RescheduleRequestAdminDTO.PreferredSlot> resolveSlots(
            String raw, Map<Integer, InterviewTimeSlot> slots) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<RescheduleRequestAdminDTO.PreferredSlot> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            Integer id = parseId(part);
            if (id == null) {
                continue;
            }
            InterviewTimeSlot ts = slots.get(id);
            out.add(RescheduleRequestAdminDTO.PreferredSlot.builder()
                    .timeSlotId(id)
                    // 时间窗被删掉时退回显示 ID，总好过凭空消失一行
                    .label(ts == null ? "时间窗 #" + id : labelOf(ts))
                    .build());
        }
        return out;
    }

    private static String labelOf(InterviewTimeSlot ts) {
        StringBuilder sb = new StringBuilder();
        if (ts.getInterviewDate() != null) {
            sb.append(ts.getInterviewDate().format(SLOT_DATE)).append(' ');
        }
        if (ts.getSlotName() != null && !ts.getSlotName().isBlank()) {
            sb.append(ts.getSlotName()).append(' ');
        }
        if (ts.getStartTime() != null && ts.getEndTime() != null) {
            sb.append(ts.getStartTime().format(SLOT_TIME)).append('-').append(ts.getEndTime().format(SLOT_TIME));
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? ("时间窗 #" + ts.getTimeSlotId()) : s;
    }

    private static Integer parseId(String part) {
        try {
            return Integer.valueOf(part.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Integer, User> batchUsers(List<InterviewRescheduleRequest> rows) {
        List<Integer> ids = rows.stream().map(InterviewRescheduleRequest::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (a, b) -> a));
    }

    private Map<Integer, InterviewSchedule> batchSchedules(List<InterviewRescheduleRequest> rows) {
        List<Integer> ids = rows.stream().map(InterviewRescheduleRequest::getScheduleId)
                .filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : interviewScheduleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(InterviewSchedule::getScheduleId, Function.identity(), (a, b) -> a));
    }

    private Map<Integer, InterviewTimeSlot> batchSlots(List<InterviewRescheduleRequest> rows) {
        List<Integer> ids = rows.stream()
                .map(InterviewRescheduleRequest::getPreferredTimeSlotIds)
                .filter(v -> v != null && !v.isBlank())
                .flatMap(v -> Arrays.stream(v.split(",")))
                .map(InterviewRescheduleController::parseId)
                .filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : interviewTimeSlotMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(InterviewTimeSlot::getTimeSlotId, Function.identity(), (a, b) -> a));
    }

    private Map<Integer, String> batchLocations(java.util.Collection<InterviewSchedule> schedules) {
        List<Integer> ids = schedules.stream().map(InterviewSchedule::getSessionId)
                .filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : interviewSessionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(InterviewSession::getSessionId,
                        s -> s.getLocation() == null ? "" : s.getLocation(), (a, b) -> a));
    }

    /**
     * 管理员处理改期申请：status=1 同意（自动取消原场次安排，进入「分配与调剂」
     * 待调剂池，由管理员人工重排到新场次）；status=2 拒绝（原安排不动）。
     */
    @PutMapping("/admin/{requestId}/handle")
    @PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
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

        // 同意改期 = 取消原场次安排（status=2），候选人进入「分配与调剂」的待调剂池。
        // 原先只改申请状态、旧安排照常生效：学生端仍显示原时间、面试官仍按旧安排等人，
        // 看起来像"系统直接定了"——同意后必须由管理员在新场次上人工重排。
        // 取消同时也解开意向锁（已取消的安排不再锁志愿，见 IntentLock 集成测试）。
        if (status == InterviewRescheduleRequest.STATUS_APPROVED && req.getScheduleId() != null) {
            InterviewSchedule schedule = interviewScheduleMapper.selectById(req.getScheduleId());
            if (schedule != null && Integer.valueOf(1).equals(schedule.getStatus())) {
                schedule.setStatus(2); // 已取消，等待人工重排
                interviewScheduleMapper.updateById(schedule);
                log.info("改期申请{}已同意，原面试安排{}已取消待重排", requestId, schedule.getScheduleId());
            }
        }

        log.info("管理员{}处理改期申请{}，status={}", handler.getUsername(), requestId, status);
        return ResponseEntity.ok(ResponseMessage.success(req));
    }
}
