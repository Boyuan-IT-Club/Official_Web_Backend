package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.ReassignScheduleRequestDTO;
import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
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

    /**
     * 查询某周期的已分配名单（可按场次过滤），按面试时间排序。
     * 供管理端「场次」查看每个场次/时间段实际分配到的候选人。
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
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (club.boyuan.official.persistence.entity.InterviewSchedule sc : schedules) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("scheduleId", sc.getScheduleId());
            item.put("resumeId", sc.getResumeId());
            item.put("userId", sc.getUserId());
            item.put("sessionId", sc.getSessionId());
            item.put("interviewTime", sc.getInterviewTime());
            item.put("status", sc.getStatus());
            item.put("syncStatus", sc.getSyncStatus());
            item.put("notifStatus", sc.getNotifStatus());
            item.put("notes", sc.getNotes());
            if (sc.getUserId() != null) {
                club.boyuan.official.persistence.entity.User u = userMapper.selectById(sc.getUserId());
                item.put("name", u != null ? (u.getName() != null ? u.getName() : u.getUsername()) : null);
                item.put("username", u != null ? u.getUsername() : null);
            }
            if (sc.getDeptId() != null) {
                club.boyuan.official.persistence.entity.Department d = departmentMapper.selectById(sc.getDeptId());
                item.put("deptName", d != null ? d.getDeptName() : null);
            }
            result.add(item);
        }
        return ResponseEntity.ok(ResponseMessage.success(result));
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
            item.put("email", u != null ? u.getEmail() : null);
            item.put("phone", u != null ? u.getPhone() : null);
            item.put("note", note);          // 学生填的说明，可能为空
            item.put("resumeStatus", resume.getStatus());
            out.add(item);
        }
        return ResponseEntity.ok(ResponseMessage.success(out));
    }
}