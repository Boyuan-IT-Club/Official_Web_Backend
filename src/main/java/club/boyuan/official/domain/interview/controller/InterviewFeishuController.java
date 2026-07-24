package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.integration.feishu.dto.FeishuSyncTaskStatusDTO;
import club.boyuan.official.integration.feishu.dto.FeishuSyncTaskSubmitDTO;
import club.boyuan.official.integration.feishu.dto.ImportFromFeishuTableRequestDTO;
import club.boyuan.official.integration.feishu.dto.ImportFeishuRequestDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.interview.service.InterviewFeishuImportService;
import jakarta.validation.Valid;
import club.boyuan.official.integration.feishu.FeishuSyncTaskStatus;
import club.boyuan.official.infra.sse.AsyncTaskChannel;
import club.boyuan.official.infra.sse.AsyncTaskSseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
 * GET  /import/tasks/{id}        → 轮询任务状态（兜底）
 * GET  /import/tasks/{id}/stream → SSE 推送任务状态（推荐）
 * </pre>
 * 实际写飞书不在本 Controller 线程，而在 MQ → FeishuSyncConsumer → FeishuImportExecutor。
 */
@RestController
@RequestMapping("/api/interview/feishu")
@RequiredArgsConstructor
@Slf4j
public class InterviewFeishuController {

    private final InterviewFeishuImportService feishuImportService;
    private final IUserService userService;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final ObjectMapper objectMapper;

    /**
     * 从飞书多维表格异步拉回平台：立即返回 taskId，轮询/SSE 与 {@link #submitImport} 相同。
     * <p>列名：姓名、录取部门、面试是否通过、面试是否通过（预选）、是否调剂、决定人。
     */
    @PostMapping("/import-from-table")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<FeishuSyncTaskSubmitDTO>> importFromTable(
            @Valid @RequestBody ImportFromFeishuTableRequestDTO request) {
        log.info("提交飞书拉回任务 cycleId={}, url={}", request.getCycleId(), request.getFeishuTableUrl());
        FeishuSyncTaskSubmitDTO result = feishuImportService.submitPullFromTableTask(
                request, resolveOperatorUserId());
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 提交飞书导入任务：立即返回 taskId，实际导入由 MQ 消费者并行执行（平台 → 飞书）。
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

    /**
     * SSE 订阅飞书导入任务进度（终态后连接自动关闭）。仍保留 GET 轮询作为兜底。
     */
    @GetMapping(value = "/import/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('resume:audit')")
    public SseEmitter streamImportTask(@PathVariable Long taskId) throws Exception {
        FeishuSyncTaskStatusDTO current = feishuImportService.getImportTaskStatus(taskId);
        SseEmitter emitter = asyncTaskSseHub.register(AsyncTaskChannel.FEISHU, String.valueOf(taskId));
        emitter.send(SseEmitter.event().name("status").data(objectMapper.writeValueAsString(current)));
        if (isTerminalFeishuStatus(current.getStatus())) {
            emitter.complete();
        }
        return emitter;
    }

    private static boolean isTerminalFeishuStatus(String status) {
        return FeishuSyncTaskStatus.SUCCESS.name().equals(status)
                || FeishuSyncTaskStatus.PARTIAL_SUCCESS.name().equals(status)
                || FeishuSyncTaskStatus.FAILED.name().equals(status);
    }

    private Integer resolveOperatorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof String s && StringUtils.hasText(s) && !"anonymousUser".equals(s)) {
            username = s;
        } else if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        }
        if (!StringUtils.hasText(username)) {
            return null;
        }
        User user = userService.getUserByUsername(username);
        return user != null ? user.getUserId() : null;
    }
}
