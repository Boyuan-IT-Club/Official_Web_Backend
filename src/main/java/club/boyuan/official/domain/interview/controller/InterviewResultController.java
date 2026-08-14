package club.boyuan.official.domain.interview.controller;


import club.boyuan.official.common.dto.*;
import club.boyuan.official.domain.interview.dto.*;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
// 本类此前没有任何类级/方法级鉴权，只落到 SecurityConfig 的 anyRequest().authenticated()，
// 也就是任何登录用户（含学生本人）都能改自己的录取结果、群发通知、拉取全体结果名单。
// 与同域的 SessionAssignmentController 对齐，统一收到 resume:audit 之下。
@PreAuthorize("hasAuthority('resume:audit')")
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

    /**
     * 批量录取 / 批量标记未通过：先勾选候选人，再一次性写入决定与录取部门。
     * 逐条录入在几十上百人时不现实，这是管理端「结果与通知」的主要入口。
     */
    @PostMapping("/batch-decision")
    public ResponseEntity<ResponseMessage<BatchDecisionResponseDTO>> batchDecision(
            @Valid @RequestBody BatchDecisionRequestDTO requestDTO
    ) {
        log.info("批量录取，cycleId={}，decision={}，deptId={}，共 {} 人",
                requestDTO.getCycleId(), requestDTO.getDecision(),
                requestDTO.getAssignedDeptId(), requestDTO.getResultIds().size());
        BatchDecisionResponseDTO responseDTO = interviewResultService.batchDecision(requestDTO);
        return ResponseEntity.ok(ResponseMessage.success(responseDTO));
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
