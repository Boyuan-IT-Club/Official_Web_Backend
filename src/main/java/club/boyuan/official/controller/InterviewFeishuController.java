package club.boyuan.official.controller;

import club.boyuan.official.dto.FeishuSyncTaskStatusDTO;
import club.boyuan.official.dto.FeishuSyncTaskSubmitDTO;
import club.boyuan.official.dto.ImportFeishuRequestDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.service.InterviewFeishuImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书同步 HTTP 入口（仅两个接口）。
 *
 * <pre>
 * POST /import              → InterviewFeishuImportService.submitImportTask
 * GET  /import/tasks/{id}   → InterviewFeishuImportService.getImportTaskStatus（前端轮询）
 * </pre>
 * 实际写飞书不在本 Controller 线程，而在 MQ → FeishuSyncConsumer → FeishuImportExecutor。
 */
@RestController
@RequestMapping("/api/interview/feishu")
@RequiredArgsConstructor
@Slf4j
public class InterviewFeishuController {

    private final InterviewFeishuImportService feishuImportService;

    /**
     * 提交飞书导入任务：立即返回 taskId，实际导入由 MQ 消费者并行执行。
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<FeishuSyncTaskSubmitDTO>> submitImport(
            @Valid @RequestBody ImportFeishuRequestDTO request) {
        log.info("提交飞书导入任务 cycleId={}, slotId={}, forceUpdate={}",
                request.getCycleId(), request.getSlotId(), request.getForceUpdate());
        FeishuSyncTaskSubmitDTO result = feishuImportService.submitImportTask(request);
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 查询飞书导入任务进度与结果（轮询）。
     */
    @GetMapping("/import/tasks/{taskId}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<FeishuSyncTaskStatusDTO>> getImportTask(
            @PathVariable Long taskId) {
        FeishuSyncTaskStatusDTO status = feishuImportService.getImportTaskStatus(taskId);
        return ResponseEntity.ok(ResponseMessage.success(status));
    }
}
