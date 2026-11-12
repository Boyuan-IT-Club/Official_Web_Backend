package club.boyuan.official.domain.agent.controller;

import java.util.Map;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.integration.agent.AgentAdminClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * 简历评估管理代理(B 模块 #135,B6/B7):Agent /admin/evaluation* 纯转发。
 *
 * 权限分三档(与 Agent 侧同码双道闸):
 * - 队列/评分卡/采纳/驳回:resume:audit(评审权,#135 用户故事 1)
 * - 预置题库读取与勾选:interview:evaluate 或 resume:audit(#128:
 *   面试官场景内看题库,不破 resume:view 全库边界)
 * - 校准案例:查看 resume:audit;录入/停用/收割 evaluation:run
 *
 * 信封/错误提取与 AgentAdminController 同约定。
 */
@RestController
@RequestMapping("/api/admin/agent/evaluation")
public class AgentEvaluationProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationProxyController.class);

    private final AgentAdminClient agentAdminClient;
    private final ObjectMapper objectMapper;

    public AgentEvaluationProxyController(AgentAdminClient agentAdminClient, ObjectMapper objectMapper) {
        this.agentAdminClient = agentAdminClient;
        this.objectMapper = objectMapper;
    }

    /** 评审队列(queue=zero|all)。 */
    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> queue(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycleId") int cycleId,
            @RequestParam(value = "queue", required = false) String queue) {
        return wrap(agentAdminClient.getEvaluationQueue(authorization, cycleId, queue));
    }

    /** 触发初筛(B2)。 */
    @PostMapping("/run")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> run(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postEvaluationRun(authorization, body));
    }

    /** job 列表。 */
    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> jobs(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycleId") int cycleId,
            @RequestParam(value = "status", required = false) String status) {
        return wrap(agentAdminClient.getEvaluationJobs(authorization, cycleId, status));
    }

    /** 单候选评分卡(面试官场景内只读)。 */
    @GetMapping("/scorecard")
    @PreAuthorize("hasAnyAuthority('interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<?>> scorecard(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("resumeId") long resumeId,
            @RequestParam("cycleId") int cycleId) {
        return wrap(agentAdminClient.getEvaluationScorecard(authorization, resumeId, cycleId));
    }

    /** 采纳(评审本人一票)。 */
    @PostMapping("/adopt")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> adopt(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postEvaluationAdopt(authorization, body));
    }

    /** 驳回。 */
    @PostMapping("/reject")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> reject(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postEvaluationReject(authorization, body));
    }

    /** 预置题库(面试官/评审)。 */
    @GetMapping("/qbank")
    @PreAuthorize("hasAnyAuthority('interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<?>> qbank(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("resumeId") long resumeId,
            @RequestParam("cycleId") int cycleId) {
        return wrap(agentAdminClient.getEvaluationQbank(authorization, resumeId, cycleId));
    }

    /** 记录面试官勾选(pick log)。 */
    @PostMapping("/qbank/pick")
    @PreAuthorize("hasAnyAuthority('interview:evaluate', 'resume:audit')")
    public ResponseEntity<ResponseMessage<?>> pick(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postEvaluationPick(authorization, body));
    }

    /** 勾选流水(resume:audit)。 */
    @GetMapping("/qbank/picks")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> picks(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("resumeId") long resumeId,
            @RequestParam("cycleId") int cycleId) {
        return wrap(agentAdminClient.getEvaluationPicks(authorization, resumeId, cycleId));
    }

    /** 失败 job 重试;includeStale 连进程重启残留一起恢复。 */
    @PostMapping("/jobs/retry")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> retryJobs(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycleId") int cycleId,
            @RequestParam(value = "includeStale", required = false, defaultValue = "false")
            boolean includeStale) {
        return wrap(agentAdminClient.postEvaluationRetry(authorization, cycleId, includeStale));
    }

    // ── 校准案例(/calibration/**):判例会被原样放进评委的 prompt,改它等于改评分的
    // 标尺——录入、停用、收割要 evaluation:run(与 Agent 侧同码),查看只要 resume:audit。
    // 查询参数名与 Agent 契约一致(snake_case),前端按契约直接传。

    /** 校准案例列表(可按清单、判定项过滤;include_retired 连已停用的一起看)。 */
    @GetMapping("/calibration/cases")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> calibrationCases(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "checklist", required = false) String checklist,
            @RequestParam(value = "item", required = false) String item,
            @RequestParam(value = "include_retired", required = false) Boolean includeRetired,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return wrap(agentAdminClient.getCalibrationCases(
                authorization, checklist, item, includeRetired, limit, offset));
    }

    /** 录入一条种子判例(201)。 */
    @PostMapping("/calibration/cases")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> addCalibrationCase(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postCalibrationCase(authorization, body));
    }

    /** 停用一条判例(Agent 侧不删行)。案例号是整数,非数字在进控制器前就被拒(400)。 */
    @DeleteMapping("/calibration/cases/{caseId}")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> retireCalibrationCase(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long caseId) {
        return wrap(agentAdminClient.deleteCalibrationCase(authorization, caseId));
    }

    /** 从已结束周期收割校准案例;功能未启用或周期还在进行中 409。 */
    @PostMapping("/calibration/harvest")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> harvestCalibrationCases(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postCalibrationHarvest(authorization, body));
    }

    private ResponseEntity<ResponseMessage<?>> wrap(ResponseEntity<String> upstream) {
        var status = upstream.getStatusCode();
        String body = upstream.getBody();
        if (status.is2xxSuccessful()) {
            return ResponseEntity.status(status).body(ResponseMessage.success(readTree(body)));
        }
        return ResponseEntity.status(status)
                .body(ResponseMessage.error(status.value(), extractDetail(body, "Agent 返回错误(" + status.value() + ")")));
    }

    private Object readTree(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (Exception e) {
            return body;
        }
    }

    private String extractDetail(String body, String fallback) {
        try {
            JsonNode node = objectMapper.readTree(body == null ? "" : body);
            for (String field : new String[] {"message", "detail"}) {
                if (node.hasNonNull(field)) {
                    return node.get(field).asText();
                }
            }
        } catch (Exception ignored) {
            // 非 JSON,走 fallback
        }
        return fallback;
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ResponseMessage<Void>> handleUnreachable(ResourceAccessException e) {
        log.error("Agent 服务不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ResponseMessage.error(502, "Agent 服务不可达,请稍后重试"));
    }
}
