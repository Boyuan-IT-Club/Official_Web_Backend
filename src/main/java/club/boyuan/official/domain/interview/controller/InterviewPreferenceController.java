package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.interview.dto.InterviewPreferenceDTO;
import club.boyuan.official.domain.interview.dto.InterviewTimeSlotDTO;
import club.boyuan.official.domain.interview.dto.SubmitInterviewPreferenceRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewTimeSlotService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生面试志愿：查看可勾选的大时段、提交志愿部门与可接受时间、查看本人志愿。
 */
@RestController
@RequestMapping("/api/interview/preference")
@RequiredArgsConstructor
@Slf4j
public class InterviewPreferenceController {

    private final IInterviewPreferenceService interviewPreferenceService;
    private final IInterviewTimeSlotService interviewTimeSlotService;
    private final IUserService userService;

    /**
     * 查询某周期可勾选的面试时间窗（仅返回开放中的）。
     */
    @GetMapping("/cycles/{cycleId}/time-slots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<InterviewTimeSlotDTO>>> listTimeSlots(@PathVariable Integer cycleId) {
        List<InterviewTimeSlotDTO> slots = interviewTimeSlotService.listByCycle(cycleId, true).stream()
                .map(InterviewPreferenceController::toTimeSlotDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ResponseMessage.success(slots));
    }

    /**
     * 提交/更新本人志愿（第一/第二志愿部门 + 多个可接受时间窗）。
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewPreferenceDTO>> submit(
            @Valid @RequestBody SubmitInterviewPreferenceRequestDTO request) {
        Integer userId = getAuthenticatedUserId();
        log.info("提交面试志愿，userId={}, cycleId={}, first={}, second={}",
                userId, request.getCycleId(), request.getFirstDeptId(), request.getSecondDeptId());
        InterviewPreferenceDTO dto = interviewPreferenceService.submitPreference(userId, request);
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    /**
     * 查询本人在指定周期的志愿；未填写时 data 为 null。
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<InterviewPreferenceDTO>> getMyPreference(@RequestParam Integer cycleId) {
        Integer userId = getAuthenticatedUserId();
        InterviewPreferenceDTO dto = interviewPreferenceService.getMyPreference(userId, cycleId);
        return ResponseEntity.ok(ResponseMessage.success(dto));
    }

    static InterviewTimeSlotDTO toTimeSlotDTO(InterviewTimeSlot ts) {
        InterviewTimeSlotDTO dto = new InterviewTimeSlotDTO();
        dto.setTimeSlotId(ts.getTimeSlotId());
        dto.setCycleId(ts.getCycleId());
        dto.setSlotName(ts.getSlotName());
        dto.setInterviewDate(ts.getInterviewDate());
        dto.setStartTime(ts.getStartTime());
        dto.setEndTime(ts.getEndTime());
        dto.setStatus(ts.getStatus());
        return dto;
    }

    private Integer getAuthenticatedUserId() {
        String username = SecurityUtil.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        return user.getUserId();
    }
}
