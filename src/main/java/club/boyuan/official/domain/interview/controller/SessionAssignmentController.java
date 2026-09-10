package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.ReassignScheduleRequestDTO;
import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeRequestDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeResponseDTO;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.ISessionAssignmentService;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员：一键场次分配、待调剂名单、人工调剂（一键再分配到其它有空的场次）。
 */
@RestController
@RequestMapping("/api/interview/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
public class SessionAssignmentController {

    private final ISessionAssignmentService sessionAssignmentService;
    private final IInterviewSessionService interviewSessionService;
    private final club.boyuan.official.persistence.mapper.InterviewScheduleMapper interviewScheduleMapper;
    private final club.boyuan.official.persistence.mapper.UserMapper userMapper;
    private final club.boyuan.official.persistence.mapper.DepartmentMapper departmentMapper;
    private final club.boyuan.official.persistence.mapper.ResumeMapper resumeMapper;
    private final club.boyuan.official.persistence.mapper.ResumeFieldDefinitionMapper resumeFieldDefinitionMapper;
    private final club.boyuan.official.persistence.mapper.ResumeFieldValueMapper resumeFieldValueMapper;
    private final club.boyuan.official.persistence.mapper.InterviewSessionMapper interviewSessionMapper;
    private final club.boyuan.official.persistence.mapper.InterviewPreferenceMapper interviewPreferenceMapper;
    private final club.boyuan.official.persistence.mapper.InterviewNotificationLogMapper notificationLogMapper;

    /**
     * 查询某周期的已分配名单（可按场次过滤），按面试时间排序。
     *
     * <p>不带 sessionId 时是全周期的统一名单——管理端「面试名单」页据此
     * 一屏看完所有场次，不必逐个场次点开。除安排本身外并入了决策时要看的
     * 信息：学号、志愿部门、场次地点，免得再去简历页对人。</p>
     *
     * <p>一律批量查询：原先每行都去 selectById 查用户和部门，一届几十人就是
     * 上百次往返，单场次时还不明显，全周期名单就很慢了。</p>
     */
    @GetMapping("/cycles/{cycleId}/schedules")
    public ResponseEntity<ResponseMessage<java.util.List<java.util.Map<String, Object>>>> listSchedules(
            @PathVariable Integer cycleId,
            @RequestParam(required = false) Integer sessionId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewSchedule> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewSchedule>()
                        .eq(club.boyuan.official.persistence.entity.InterviewSchedule::getCycleId, cycleId)
                        .orderByAsc(club.boyuan.official.persistence.entity.InterviewSchedule::getInterviewTime);
        if (sessionId != null) {
            qw.eq(club.boyuan.official.persistence.entity.InterviewSchedule::getSessionId, sessionId);
        }
        java.util.List<club.boyuan.official.persistence.entity.InterviewSchedule> schedules =
                interviewScheduleMapper.selectList(qw);
        if (schedules.isEmpty()) {
            return ResponseEntity.ok(ResponseMessage.success(java.util.List.of()));
        }

        java.util.Set<Integer> userIds = new java.util.HashSet<>();
        java.util.Set<Integer> resumeIds = new java.util.HashSet<>();
        java.util.Set<Integer> sessionIds = new java.util.HashSet<>();
        for (club.boyuan.official.persistence.entity.InterviewSchedule sc : schedules) {
            if (sc.getUserId() != null) userIds.add(sc.getUserId());
            if (sc.getResumeId() != null) resumeIds.add(sc.getResumeId());
            if (sc.getSessionId() != null) sessionIds.add(sc.getSessionId());
        }

        java.util.Map<Integer, club.boyuan.official.persistence.entity.User> users = userIds.isEmpty()
                ? java.util.Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(java.util.stream.Collectors.toMap(
                        club.boyuan.official.persistence.entity.User::getUserId, u -> u, (a, b) -> a));
        java.util.Map<Integer, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        club.boyuan.official.persistence.entity.Department::getDeptId,
                        club.boyuan.official.persistence.entity.Department::getDeptName, (a, b) -> a));
        java.util.Map<Integer, club.boyuan.official.persistence.entity.InterviewSession> sessions = sessionIds.isEmpty()
                ? java.util.Map.of()
                : interviewSessionMapper.selectBatchIds(sessionIds).stream().collect(java.util.stream.Collectors.toMap(
                        club.boyuan.official.persistence.entity.InterviewSession::getSessionId, x -> x, (a, b) -> a));
        java.util.Map<Integer, club.boyuan.official.persistence.entity.InterviewPreference> prefs = resumeIds.isEmpty()
                ? java.util.Map.of()
                : interviewPreferenceMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewPreference>()
                                .in(club.boyuan.official.persistence.entity.InterviewPreference::getResumeId, resumeIds))
                        .stream().collect(java.util.stream.Collectors.toMap(
                                club.boyuan.official.persistence.entity.InterviewPreference::getResumeId, x -> x, (a, b) -> a));
        // 学号在简历字段里，不在 user 上（user.username 多数是学号但早期账号是拼音）
        java.util.Map<Integer, String> studentIds = studentIdsOf(cycleId, resumeIds);

        /*
         * 三类面试通知各自发到没发到。
         *
         * 原来这一列读的是 interview_schedule.notif_status，而那个字段只在发送
         * 「面试安排通知」时才置 1，别的通知发出去它一动不动——列名叫「通知」
         * 却只代表一类，用户发完初筛通知回头看名单，看到的还是「未通知」。
         * 改为直接问通知日志，它才是发送的真实记录。
         */
        java.util.Set<Integer> arrangedSent = sentSchedules("BOOKING_SUCCESS", scheduleIdsOf(schedules));
        java.util.Set<Integer> eveSent = sentSchedules("EVE_REMINDER", scheduleIdsOf(schedules));
        java.util.Set<Integer> daySent = sentSchedules("DAY_REMINDER", scheduleIdsOf(schedules));

        // 简历状态：初筛未通过的人不该还占着场次，名单上要标出来
        java.util.Map<Integer, Integer> resumeStatus = resumeIds.isEmpty()
                ? java.util.Map.of()
                : resumeMapper.selectBatchIds(resumeIds).stream()
                        .filter(r -> r.getStatus() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                club.boyuan.official.persistence.entity.Resume::getResumeId,
                                club.boyuan.official.persistence.entity.Resume::getStatus, (a, b) -> a));

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (club.boyuan.official.persistence.entity.InterviewSchedule sc : schedules) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("scheduleId", sc.getScheduleId());
            item.put("resumeId", sc.getResumeId());
            item.put("userId", sc.getUserId());
            item.put("sessionId", sc.getSessionId());
            item.put("interviewTime", sc.getInterviewTime());
            item.put("timeOverridden", sc.getTimeOverridden());
            item.put("status", sc.getStatus());
            item.put("syncStatus", sc.getSyncStatus());
            item.put("notifStatus", sc.getNotifStatus());
            item.put("notes", sc.getNotes());
            club.boyuan.official.persistence.entity.User u =
                    sc.getUserId() == null ? null : users.get(sc.getUserId());
            item.put("name", u != null ? (u.getName() != null ? u.getName() : u.getUsername()) : null);
            item.put("username", u != null ? u.getUsername() : null);
            item.put("studentId", studentIds.get(sc.getResumeId()));
            item.put("deptName", sc.getDeptId() == null ? null : deptNames.get(sc.getDeptId()));
            club.boyuan.official.persistence.entity.InterviewSession ss =
                    sc.getSessionId() == null ? null : sessions.get(sc.getSessionId());
            item.put("location", ss != null ? ss.getLocation() : null);
            club.boyuan.official.persistence.entity.InterviewPreference pf =
                    sc.getResumeId() == null ? null : prefs.get(sc.getResumeId());
            item.put("firstDeptName", pf == null || pf.getFirstDeptId() == null
                    ? null : deptNames.get(pf.getFirstDeptId()));
            item.put("secondDeptName", pf == null || pf.getSecondDeptId() == null
                    ? null : deptNames.get(pf.getSecondDeptId()));
            item.put("notifiedArranged", arrangedSent.contains(sc.getScheduleId()));
            item.put("notifiedEve", eveSent.contains(sc.getScheduleId()));
            item.put("notifiedDay", daySent.contains(sc.getScheduleId()));
            item.put("resumeStatus", sc.getResumeId() == null ? null : resumeStatus.get(sc.getResumeId()));
            result.add(item);
        }
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    private static java.util.Set<Integer> scheduleIdsOf(
            java.util.List<club.boyuan.official.persistence.entity.InterviewSchedule> schedules) {
        return schedules.stream()
                .map(club.boyuan.official.persistence.entity.InterviewSchedule::getScheduleId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 某类通知实际发到了哪些安排上 */
    private java.util.Set<Integer> sentSchedules(String type, java.util.Set<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return java.util.Set.of();
        }
        return notificationLogMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewNotificationLog>()
                                .eq(club.boyuan.official.persistence.entity.InterviewNotificationLog::getNotificationType, type)
                                .in(club.boyuan.official.persistence.entity.InterviewNotificationLog::getScheduleId, scheduleIds))
                .stream()
                .map(club.boyuan.official.persistence.entity.InterviewNotificationLog::getScheduleId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 批量取这批简历里填的学号；本届没有学号字段时返回空表 */
    private java.util.Map<Integer, String> studentIdsOf(Integer cycleId, java.util.Set<Integer> resumeIds) {
        if (resumeIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.List<club.boyuan.official.persistence.entity.ResumeFieldDefinition> defs =
                resumeFieldDefinitionMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.ResumeFieldDefinition>()
                                .eq(club.boyuan.official.persistence.entity.ResumeFieldDefinition::getCycleId, cycleId));
        Integer fieldId = defs.stream()
                .filter(d -> d.getFieldLabel() != null && d.getFieldLabel().contains("学号"))
                .map(club.boyuan.official.persistence.entity.ResumeFieldDefinition::getFieldId)
                .findFirst().orElse(null);
        if (fieldId == null) {
            return java.util.Map.of();
        }
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        for (club.boyuan.official.persistence.entity.ResumeFieldValue v : resumeFieldValueMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.ResumeFieldValue>()
                        .in(club.boyuan.official.persistence.entity.ResumeFieldValue::getResumeId, resumeIds)
                        .eq(club.boyuan.official.persistence.entity.ResumeFieldValue::getFieldId, fieldId))) {
            if (v.getFieldValue() != null && !v.getFieldValue().isBlank()) {
                out.put(v.getResumeId(), v.getFieldValue());
            }
        }
        return out;
    }

    /**
     * 取消若干条面试安排（管理端）。
     *
     * 用途是把初筛之后才被刷掉的人从场次里摘出来：他们的安排是初筛之前排的，
     * 初筛不会回收，于是面试官白等一个不会来的人，场次名额也一直被占着。
     *
     * 取消 = 安排置为已取消 + 清空面试时间 + 归还场次名额。
     * 名额要减回去，否则「已占 5/5」是假的，后面的人再也排不进这个场次。
     */
    @PostMapping("/cycles/{cycleId}/schedules/cancel")
    public ResponseEntity<ResponseMessage<java.util.Map<String, Object>>> cancelSchedules(
            @PathVariable Integer cycleId,
            @RequestBody java.util.Map<String, Object> body) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (body.get("scheduleIds") instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                try {
                    ids.add(Integer.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // 单个脏值不该让整批失败
                }
            }
        }
        if (ids.isEmpty()) {
            throw new club.boyuan.official.common.exception.BusinessException(
                    club.boyuan.official.common.exception.BusinessExceptionEnum.MISSING_REQUIRED_FIELD,
                    "请提供 scheduleIds");
        }

        // 归属校验：只认属于本周期且仍生效的安排，免得请求里夹带别届的 id
        java.util.List<club.boyuan.official.persistence.entity.InterviewSchedule> targets =
                interviewScheduleMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewSchedule>()
                                .eq(club.boyuan.official.persistence.entity.InterviewSchedule::getCycleId, cycleId)
                                .eq(club.boyuan.official.persistence.entity.InterviewSchedule::getStatus, 1)
                                .in(club.boyuan.official.persistence.entity.InterviewSchedule::getScheduleId, ids));

        java.util.Map<Integer, Long> freedPerSession = new java.util.HashMap<>();
        for (club.boyuan.official.persistence.entity.InterviewSchedule sc : targets) {
            if (sc.getSessionId() != null) {
                freedPerSession.merge(sc.getSessionId(), 1L, Long::sum);
            }
            sc.setStatus(2).setInterviewTime(null).setSyncStatus(0).setNotifStatus(0);
            interviewScheduleMapper.updateById(sc);
        }
        for (java.util.Map.Entry<Integer, Long> e : freedPerSession.entrySet()) {
            club.boyuan.official.persistence.entity.InterviewSession sess =
                    interviewSessionMapper.selectById(e.getKey());
            if (sess == null) continue;
            int occupied = sess.getCurrentOccupied() == null ? 0 : sess.getCurrentOccupied();
            // 不减到负数：历史数据里 current_occupied 可能本来就对不上
            interviewSessionMapper.updateById(new club.boyuan.official.persistence.entity.InterviewSession()
                    .setSessionId(sess.getSessionId())
                    .setCurrentOccupied(Math.max(0, occupied - e.getValue().intValue())));
        }

        java.util.List<Integer> done = targets.stream()
                .map(club.boyuan.official.persistence.entity.InterviewSchedule::getScheduleId).toList();
        java.util.List<Integer> skipped = ids.stream().filter(i -> !done.contains(i)).toList();
        log.info("管理员取消面试安排，cycleId={}，取消 {} 条，跳过 {} 条", cycleId, done.size(), skipped.size());
        return ResponseEntity.ok(ResponseMessage.success(
                java.util.Map.of("cancelled", done.size(), "skipped", skipped)));
    }

    /**
     * 为某周期一键分配面试场次（可重复执行，仅处理尚未分配的候选人）。
     */
    @PostMapping("/cycles/{cycleId}/assign")
    public ResponseEntity<ResponseMessage<SessionAssignmentResultDTO>> assign(@PathVariable Integer cycleId) {
        log.info("管理员触发场次分配，cycleId={}", cycleId);
        SessionAssignmentResultDTO result = sessionAssignmentService.assign(cycleId);
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 待人工调剂名单：已填志愿但未分到场次的候选人。
     */
    @GetMapping("/cycles/{cycleId}/unassigned")
    public ResponseEntity<ResponseMessage<List<SessionAssignmentResultDTO.UnassignedItem>>> listUnassigned(
            @PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(sessionAssignmentService.listUnassigned(cycleId)));
    }

    /**
     * 可用场次（还有剩余名额），用于人工调剂选择目标。可按部门过滤。
     */
    @GetMapping("/cycles/{cycleId}/available-sessions")
    public ResponseEntity<ResponseMessage<List<InterviewSessionDTO>>> listAvailableSessions(
            @PathVariable Integer cycleId,
            @RequestParam(required = false) Integer deptId) {
        return ResponseEntity.ok(ResponseMessage.success(
                interviewSessionService.listSessionDTOs(cycleId, deptId, true)));
    }

    /**
     * 人工调剂：把某位候选人（按简历ID）一键分配 / 再分配到目标场次。
     */
    @PostMapping("/preferences/{resumeId}/assign")
    public ResponseEntity<ResponseMessage<SessionAssignmentResultDTO.AssignedItem>> manualAssign(
            @PathVariable Integer resumeId,
            @Valid @RequestBody ReassignScheduleRequestDTO request) {
        log.info("人工调剂，resumeId={}, targetSessionId={}", resumeId, request.getTargetSessionId());
        SessionAssignmentResultDTO.AssignedItem item =
                sessionAssignmentService.manualAssign(resumeId, request.getTargetSessionId());
        return ResponseEntity.ok(ResponseMessage.success(item));
    }

    /**
     * 手动把某条面试安排的 interview_time 调整到精确的几点几分。
     * <p>标记该条时间为「人工指定」，并把 sync_status / notif_status 重置为 0
     * 以触发飞书重新同步与后续提醒。越界或同场次时间冲突只告警不拒绝。</p>
     */
    @PutMapping("/schedules/{scheduleId}/interview-time")
    public ResponseEntity<ResponseMessage<UpdateInterviewTimeResponseDTO>> updateInterviewTime(
            @PathVariable Integer scheduleId,
            @Valid @RequestBody UpdateInterviewTimeRequestDTO request) {
        log.info("管理员手动调整面试时间 scheduleId={}, interviewTime={}", scheduleId, request.getInterviewTime());
        UpdateInterviewTimeResponseDTO result =
                sessionAssignmentService.updateInterviewTime(scheduleId, request.getInterviewTime());
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 无法参加线下面试的同学名单。
     *
     * 这批人不会被自动排进场次，管理员得单独约线上面试——可在此之前
     * 他们在管理端是「看不见」的：既不在已分配名单里，也不在任何场次下，
     * 只能靠翻每一份简历才发现。
     *
     * 数据来自简历字段 expected_interview_time 的 JSON
     * （{first, second, canAttend, customTime}）——「能否线下参加」没有独立
     * 字段，是面试意向卡一次性写进去的；customTime 承载学生填的说明。
     * 这里不为它建新表：值本就在简历里，另存一份只会产生第二个真相源。
     */
    @GetMapping("/cycles/{cycleId}/offline-unavailable")
    public ResponseEntity<ResponseMessage<List<Map<String, Object>>>> offlineUnavailable(
            @PathVariable Integer cycleId) {
        List<Map<String, Object>> out = new ArrayList<>();

        // 先定位本周期「能否线下参加」所在的字段 id：字段是按周期配置的，
        // 不同周期同一个 fieldKey 的 id 不一样，写死 id 会串届
        ResumeFieldDefinition def = resumeFieldDefinitionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, cycleId)
                        .eq(ResumeFieldDefinition::getFieldKey, "expected_interview_time")
                        .last("LIMIT 1"));
        if (def == null) {
            return ResponseEntity.ok(ResponseMessage.success(out));
        }

        // 学号要取简历里填的那个，不能拿 user.username 顶替。
        // 多数同学两者恰好相同（注册时用学号当用户名），但早期账号不是——
        // 线上就有登录名为 "dinghuaye"、简历里学号是 10245101480 的情况，
        // 表头写着「学号」却显示登录名，看的人会以为数据错了。
        ResumeFieldDefinition sidDef = resumeFieldDefinitionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResumeFieldDefinition>()
                        .eq(ResumeFieldDefinition::getCycleId, cycleId)
                        .eq(ResumeFieldDefinition::getFieldKey, "student_id")
                        .last("LIMIT 1"));

        List<ResumeFieldValue> values = resumeFieldValueMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResumeFieldValue>()
                        .eq(ResumeFieldValue::getFieldId, def.getFieldId()));

        for (ResumeFieldValue v : values) {
            String raw = v.getFieldValue();
            if (raw == null || !raw.contains("\"no\"")) {
                continue;   // 便宜的预筛，避免给每一行都解析 JSON
            }
            String canAttend;
            String note;
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
                canAttend = node.path("canAttend").asText("");
                note = node.path("customTime").asText("");
            } catch (Exception e) {
                continue;   // 脏数据跳过，不能让一行坏 JSON 弄挂整张名单
            }
            if (!"no".equals(canAttend)) {
                continue;
            }

            Resume resume = v.getResumeId() == null ? null : resumeMapper.selectById(v.getResumeId());
            if (resume == null || !cycleId.equals(resume.getCycleId())) {
                continue;
            }
            User u = resume.getUserId() == null ? null : userMapper.selectById(resume.getUserId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", resume.getUserId());
            item.put("resumeId", resume.getResumeId());
            item.put("name", u != null ? u.getName() : null);
            item.put("username", u != null ? u.getUsername() : null);
            item.put("studentId", studentIdOf(resume.getResumeId(), sidDef));
            item.put("email", u != null ? u.getEmail() : null);
            item.put("phone", u != null ? u.getPhone() : null);
            item.put("note", note);          // 学生填的说明，可能为空
            item.put("resumeStatus", resume.getStatus());
            out.add(item);
        }
        return ResponseEntity.ok(ResponseMessage.success(out));
    }

    /** 取某份简历里填的学号；没填或没有该字段时返回 null（由前端回落到登录名）。 */
    private String studentIdOf(Integer resumeId, ResumeFieldDefinition sidDef) {
        if (resumeId == null || sidDef == null) {
            return null;
        }
        ResumeFieldValue v = resumeFieldValueMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResumeFieldValue>()
                        .eq(ResumeFieldValue::getResumeId, resumeId)
                        .eq(ResumeFieldValue::getFieldId, sidDef.getFieldId())
                        .last("LIMIT 1"));
        if (v == null || v.getFieldValue() == null || v.getFieldValue().trim().isEmpty()) {
            return null;
        }
        return v.getFieldValue().trim();
    }
}