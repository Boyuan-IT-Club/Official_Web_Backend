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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * 周期复盘管理代理:Agent /admin/retrospective/* 纯转发(指标快照的采集与读取,
 * 报告的生成、审阅与发布),不做管理逻辑。
 *
 * 权限分两档(与 Agent 侧同码双道闸,没有新增权限码):
 * - 触发采集、生成报告、发布:evaluation:run(与触发初筛同一个执行权)
 * - 读快照、读报告:resume:audit 或 evaluation:run
 *
 * 报告草稿只给持 evaluation:run 的人看——这一层裁决在 Agent(它按令牌里的权限码
 * 决定只在已发布版本里找还是看全部),这里不重复实现,只把令牌原样带过去。
 *
 * 查询参数名与 Agent 契约一致(snake_case),前端按契约直接传。
 * 信封/错误提取与 AgentAdminController 同约定:上游状态码保持(202/404/409/503
 * 都原样给前端),2xx 包 success 信封,其余提取 detail 转同状态码 error。
 */
@RestController
@RequestMapping("/api/admin/agent/retrospective")
public class AgentRetrospectiveProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentRetrospectiveProxyController.class);

    private final AgentAdminClient agentAdminClient;
    private final ObjectMapper objectMapper;

    public AgentRetrospectiveProxyController(AgentAdminClient agentAdminClient, ObjectMapper objectMapper) {
        this.agentAdminClient = agentAdminClient;
        this.objectMapper = objectMapper;
    }

    /** 采集并保存一个周期的指标快照(内容与最新一版相同则不落新版本)。 */
    @PostMapping("/snapshot")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> takeSnapshot(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postRetrospectiveSnapshot(authorization, body));
    }

    /** 读最新一版或指定 version 的快照;带 baseline_cycle_id 时附差值。 */
    @GetMapping("/snapshot")
    @PreAuthorize("hasAnyAuthority('resume:audit', 'evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> snapshot(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycle_id") int cycleId,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "baseline_cycle_id", required = false) Integer baselineCycleId) {
        return wrap(agentAdminClient.getRetrospectiveSnapshot(authorization, cycleId, version, baselineCycleId));
    }

    /** 一个周期的快照版本列表与各域采集状态(不含指标正文)。 */
    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyAuthority('resume:audit', 'evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> snapshots(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycle_id") int cycleId) {
        return wrap(agentAdminClient.getRetrospectiveSnapshots(authorization, cycleId));
    }

    /** 生成一版报告草稿(后台执行,202);同周期已有一份在生成 409,功能未开启 503。 */
    @PostMapping("/run")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> runReport(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postRetrospectiveRun(authorization, body));
    }

    /** 读最新一版或指定 version 的报告(没有执行权的人只看得到已发布的,由 Agent 裁决)。 */
    @GetMapping("/report")
    @PreAuthorize("hasAnyAuthority('resume:audit', 'evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> report(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycle_id") int cycleId,
            @RequestParam(value = "version", required = false) Integer version) {
        return wrap(agentAdminClient.getRetrospectiveReport(authorization, cycleId, version));
    }

    /** 一个周期的报告版本列表(不含正文)。 */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('resume:audit', 'evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> reports(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("cycle_id") int cycleId) {
        return wrap(agentAdminClient.getRetrospectiveReports(authorization, cycleId));
    }

    /** 发布一个草稿版本(该周期原先已发布的那版转 superseded)。 */
    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('evaluation:run')")
    public ResponseEntity<ResponseMessage<?>> publishReport(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postRetrospectivePublish(authorization, body));
    }

    /** 上游响应 → 站内统一信封(与 AgentAdminController 同约定)。 */
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
            return body; // 非 JSON 上游响应,按字符串透传
        }
    }

    /** Agent 错误体是 FastAPI {"detail": ...};取人话文案给前端 toast。 */
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

    /** Agent 不可达(含读超时):502。只记异常消息,不记请求体与响应体。 */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ResponseMessage<Void>> handleUnreachable(ResourceAccessException e) {
        log.error("Agent 服务不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ResponseMessage.error(502, "Agent 服务不可达,请稍后重试"));
    }
}
