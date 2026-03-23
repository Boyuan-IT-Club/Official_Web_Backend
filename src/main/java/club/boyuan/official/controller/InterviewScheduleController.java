package club.boyuan.official.controller;

import club.boyuan.official.dto.AutoAssignInterviewResponseDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.service.IInterviewScheduleService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 面试安排表 前端控制器
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/schedule")
@AllArgsConstructor
public class InterviewScheduleController {

    private final IInterviewScheduleService interviewScheduleService;

    /**
     * 一键分配面试成员面试时间地点（按招募周期）- 路径参数版本
     * 
     * @param cycleId 招募周期ID
     * @return 分配结果
     */
    @PostMapping("/auto-assign/{cycleId}")
    @PreAuthorize("hasAuthority(('resume:audit'))")
    public ResponseEntity<ResponseMessage<AutoAssignInterviewResponseDTO>> autoAssignInterviewsByCycleId(
            @PathVariable Integer cycleId) {
        try {
            log.info("开始一键分配面试，招募周期ID: {}", cycleId);
            AutoAssignInterviewResponseDTO result = interviewScheduleService.autoAssignInterviews(cycleId);
            log.info("一键分配面试完成，已分配 {} 人", result.getAssignedCount());
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (Exception e) {
            log.error("一键分配面试失败，招募周期ID: {}", cycleId, e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "分配失败: " + e.getMessage()));
        }
    }
}