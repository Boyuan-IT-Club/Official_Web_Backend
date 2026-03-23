package club.boyuan.official.controller;


import club.boyuan.official.dto.*;
import club.boyuan.official.entity.InterviewResult;
import club.boyuan.official.service.IInterviewResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 面试结果表 前端控制器
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@RestController
@RequestMapping("/api/interview/result")
@Slf4j
@RequiredArgsConstructor
public class InterviewResultController {

    private final IInterviewResultService interviewResultService;
    @PostMapping("/send-notifications")
    public ResponseEntity<ResponseMessage<SendNotificationsResponseDTO>> sendNotifications(
            @Valid @RequestBody SendNotificationsRequestDTO requestDTO
    ) {
        try {
            log.info("发送面试结果通知,通知类型{},结果id数量{}", requestDTO.getNotificationType(), requestDTO.getResultIds().size());
            SendNotificationsResponseDTO responseDTO = interviewResultService.sendNotifications(requestDTO);
            return ResponseEntity.ok(ResponseMessage.success(responseDTO));
        } catch (Exception e) {
            log.error("发送面试结果通知失败", e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "发送面试结果通知失败"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseMessage<InterviewResultResponseDTO>> list(
            @RequestParam Integer cycleId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            log.info("获取面试结果列表");
            InterviewResultResponseDTO responseDTO = interviewResultService.list(cycleId, name, decision, department, page, size);
            ResponseMessage<InterviewResultResponseDTO> response = ResponseMessage.success(responseDTO);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取面试结果列表失败", e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "获取面试结果列表失败"));
        }

    }

    @PutMapping("/update/{resultId}")
    public ResponseEntity<ResponseMessage<InterviewResult>> update(@PathVariable Integer resultId,
                                                                   @Valid @RequestBody InterviewResultSaveDTO interviewResult) {
        try {
            log.info("更新面试结果");
            InterviewResult result = interviewResultService.update(resultId, interviewResult);
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (Exception e) {
            log.error("更新面试结果失败", e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "更新面试结果失败"));
        }
    }

    //根据resultId获取面试结果
    @GetMapping("/get/{resultId}")
    public ResponseEntity<ResponseMessage<InterviewResult>> get(@PathVariable Integer resultId) {
        try {
            log.info("获取面试结果");
            InterviewResult result = interviewResultService.getById(resultId);
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (Exception e) {
            log.error("获取面试结果失败", e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "获取面试结果失败"));
        }
    }
}
