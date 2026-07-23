package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.ReassignScheduleRequestDTO;
import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.ISessionAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员：一键场次分配、待调剂名单、人工调剂（一键再分配到其它有空的场次）。
 */
@RestController
@RequestMapping("/api/interview/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('resume:audit')")
public class SessionAssignmentController {

    private final ISessionAssignmentService sessionAssignmentService;
    private final IInterviewSessionService interviewSessionService;

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
}
