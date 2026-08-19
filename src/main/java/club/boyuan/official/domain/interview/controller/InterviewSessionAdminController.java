package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.CreateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.dto.CreateInterviewTimeSlotRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.InterviewTimeSlotDTO;
import club.boyuan.official.domain.interview.dto.SaveSessionInterviewersRequestDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeSlotRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewSessionService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.interview.service.ISessionInterviewerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员维护面试时间窗与场次（部门×时间窗×地点×容量）。
 */
@RestController
@RequestMapping("/api/interview/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
public class InterviewSessionAdminController {

    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final IInterviewSessionService interviewSessionService;
    private final ISessionInterviewerService sessionInterviewerService;

    // ------------------------------------------------------------- 时间窗

    @PostMapping("/time-slots")
    public ResponseEntity<ResponseMessage<InterviewTimeSlotDTO>> createTimeSlot(
            @Valid @RequestBody CreateInterviewTimeSlotRequestDTO request) {
        InterviewTimeSlotDTO dto = InterviewPreferenceController.toTimeSlotDTO(
                interviewTimeSlotService.createTimeSlot(request));
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    @PutMapping("/time-slots/{timeSlotId}")
    public ResponseEntity<ResponseMessage<InterviewTimeSlotDTO>> updateTimeSlot(
            @PathVariable Integer timeSlotId,
            @RequestBody UpdateInterviewTimeSlotRequestDTO request) {
        InterviewTimeSlotDTO dto = InterviewPreferenceController.toTimeSlotDTO(
                interviewTimeSlotService.updateTimeSlot(timeSlotId, request));
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    @DeleteMapping("/time-slots/{timeSlotId}")
    public ResponseEntity<ResponseMessage<Void>> deleteTimeSlot(@PathVariable Integer timeSlotId) {
        interviewTimeSlotService.deleteTimeSlot(timeSlotId);
        return ResponseEntity.ok(ResponseMessage.success());
    }

    @GetMapping("/cycles/{cycleId}/time-slots")
    public ResponseEntity<ResponseMessage<List<InterviewTimeSlotDTO>>> listTimeSlots(@PathVariable Integer cycleId) {
        List<InterviewTimeSlotDTO> slots = interviewTimeSlotService.listByCycle(cycleId, false).stream()
                .map(InterviewPreferenceController::toTimeSlotDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ResponseMessage.success(slots));
    }

    // -------------------------------------------------------------- 场次

    @PostMapping("/sessions")
    public ResponseEntity<ResponseMessage<InterviewSessionDTO>> createSession(
            @Valid @RequestBody CreateInterviewSessionRequestDTO request) {
        InterviewSessionDTO dto = interviewSessionService.toDTO(interviewSessionService.createSession(request));
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseMessage<InterviewSessionDTO>> updateSession(
            @PathVariable Integer sessionId,
            @RequestBody UpdateInterviewSessionRequestDTO request) {
        InterviewSessionDTO dto = interviewSessionService.toDTO(interviewSessionService.updateSession(sessionId, request));
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseMessage<Void>> deleteSession(@PathVariable Integer sessionId) {
        interviewSessionService.deleteSession(sessionId);
        return ResponseEntity.ok(ResponseMessage.success());
    }

    @GetMapping("/cycles/{cycleId}/sessions")
    public ResponseEntity<ResponseMessage<List<InterviewSessionDTO>>> listSessions(
            @PathVariable Integer cycleId,
            @RequestParam(required = false) Integer deptId) {
        List<InterviewSessionDTO> sessions = interviewSessionService.listSessionDTOs(cycleId, deptId, false);
        return ResponseEntity.ok(ResponseMessage.success(sessions));
    }

    // ---------------------------------------------------------- 场次面试官

    /**
     * 覆盖式绑定该场次的面试官，决定评价表里谁有自己的评分列、以及「我的待评价」怎么过滤。
     */
    @PutMapping("/sessions/{sessionId}/interviewers")
    public ResponseEntity<ResponseMessage<List<Integer>>> bindInterviewers(
            @PathVariable Integer sessionId,
            @Valid @RequestBody SaveSessionInterviewersRequestDTO request) {
        return ResponseEntity.ok(ResponseMessage.success(
                sessionInterviewerService.bindInterviewers(sessionId, request.getUserIds())));
    }

    @GetMapping("/sessions/{sessionId}/interviewers")
    public ResponseEntity<ResponseMessage<List<Integer>>> listInterviewers(@PathVariable Integer sessionId) {
        return ResponseEntity.ok(ResponseMessage.success(
                sessionInterviewerService.listInterviewerIds(sessionId)));
    }
}
