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
import club.boyuan.official.domain.interview.dto.LocationTableConfigDTO;
import club.boyuan.official.domain.interview.dto.SaveLocationTableRequestDTO;
import club.boyuan.official.domain.interview.service.ILocationTableService;
import club.boyuan.official.domain.interview.dto.PullAllLocationsResponseDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
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
    private final ILocationTableService locationTableService;
    private final IUserService userService;
    private final AsyncTaskSseHub asyncTaskSseHub;
    private final ObjectMapper objectMapper;

    /**
     * 列出该周期的面试地点及各自的飞书表格链接配置。
     * <p>推送是按地点分桶的：每个地点推到自己那张表。这个接口让管理端看清
     * 「哪些地点还没配链接（配之前推送会跳过该地点）、配了会推多少人」。
     */
    @GetMapping("/cycles/{cycleId}/locations")
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<LocationTableConfigDTO>>> listLocations(
            @PathVariable Integer cycleId) {
        return ResponseEntity.ok(ResponseMessage.success(locationTableService.listByCycle(cycleId)));
    }

    /**
     * 保存某地点的飞书表格链接；链接留空表示清除该地点的配置。
     */
    @PutMapping("/cycles/{cycleId}/locations")
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<LocationTableConfigDTO>>> saveLocation(
            @PathVariable Integer cycleId,
            @Valid @RequestBody SaveLocationTableRequestDTO request) {
        locationTableService.save(cycleId, request);
        return ResponseEntity.ok(ResponseMessage.success(locationTableService.listByCycle(cycleId)));
    }

    /**
     * 一键从「所有已配置链接的地点」拉回结果：每个地点提交一个独立任务，返回 taskId 列表。
     * <p>不做成"一个任务拉 N 张表"，是因为拉回的进度、失败行与错误信息天然按表格分开，
     * 混在一个任务里出问题时分不清是哪个地点的表有问题。
     */
    @PostMapping("/cycles/{cycleId}/pull-all")
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
    public ResponseEntity<ResponseMessage<PullAllLocationsResponseDTO>> pullAllLocations(
            @PathVariable Integer cycleId,
            @RequestParam(defaultValue = "true") Boolean updateUserDept) {
        List<LocationTableConfigDTO> configs = locationTableService.listByCycle(cycleId);
        List<PullAllLocationsResponseDTO.LocationTask> tasks = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Integer operator = resolveOperatorUserId();

        for (LocationTableConfigDTO config : configs) {
            if (config.getFeishuTableUrl() == null || config.getFeishuTableUrl().isBlank()) {
                skipped.add(config.getLocation());
                continue;
            }
            ImportFromFeishuTableRequestDTO request = new ImportFromFeishuTableRequestDTO();
            request.setCycleId(cycleId);
            request.setFeishuTableUrl(config.getFeishuTableUrl());
            request.setUpdateUserDept(updateUserDept);
            FeishuSyncTaskSubmitDTO submit = feishuImportService.submitPullFromTableTask(request, operator);
            tasks.add(new PullAllLocationsResponseDTO.LocationTask(config.getLocation(), submit.getTaskId()));
        }

        log.info("一键拉回全部地点 cycleId={}，提交 {} 个任务，跳过 {} 个未配链接的地点",
                cycleId, tasks.size(), skipped.size());
        return ResponseEntity.ok(ResponseMessage.success(
                new PullAllLocationsResponseDTO().setTasks(tasks).setSkippedLocations(skipped)));
    }

    /**
     * 从飞书多维表格异步拉回平台：立即返回 taskId，轮询/SSE 与 {@link #submitImport} 相同。
     * <p>列名：姓名、录取部门、面试是否通过、面试是否通过（预选）、是否调剂、决定人。
     */
    @PostMapping("/import-from-table")
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
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
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
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
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
    public ResponseEntity<ResponseMessage<FeishuSyncTaskStatusDTO>> getImportTask(
            @PathVariable Long taskId) {
        FeishuSyncTaskStatusDTO status = feishuImportService.getImportTaskStatus(taskId);
        return ResponseEntity.ok(ResponseMessage.success(status));
    }

    /**
     * SSE 订阅飞书导入任务进度（终态后连接自动关闭）。仍保留 GET 轮询作为兜底。
     */
    @GetMapping(value = "/import/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('feishu:sync', 'resume:audit')")
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
