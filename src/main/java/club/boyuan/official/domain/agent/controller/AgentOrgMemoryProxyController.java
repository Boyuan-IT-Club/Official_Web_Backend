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
 * 组织记忆管理代理:Agent /admin/org-memory* 纯转发(列表、勾选、退役、续期),
 * 不做管理逻辑。
 *
 * 组织记忆是往届复盘里经人确认过的经验,会被召回进管理员与社员的对话——
 * 勾选、退役、续期都是人工决策。权限全部是 evaluation:run(与发布复盘报告同级,
 * Agent 侧同码自校,双道闸;没有新增权限码)。
 *
 * 要操作哪一条(memory_id)在请求体里,不在路径上;请求体原样透传。
 * 信封/错误提取与 AgentAdminController 同约定:上游状态码保持(201/404/409/422/503
 * 都原样给前端),2xx 包 success 信封,其余提取 detail 转同状态码 error。
 */
@RestController
@RequestMapping("/api/admin/agent/org-memory")
@PreAuthorize("hasAuthority('evaluation:run')")
public class AgentOrgMemoryProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentOrgMemoryProxyController.class);

    private final AgentAdminClient agentAdminClient;
    private final ObjectMapper objectMapper;

    public AgentOrgMemoryProxyController(AgentAdminClient agentAdminClient, ObjectMapper objectMapper) {
        this.agentAdminClient = agentAdminClient;
        this.objectMapper = objectMapper;
    }

    /** 全部组织记忆(最近勾选的在前);status 可选 approved / retired。 */
    @GetMapping
    public ResponseEntity<ResponseMessage<?>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "status", required = false) String status) {
        return wrap(agentAdminClient.getOrgMemories(authorization, status));
    }

    /** 勾选已发布报告里的一条候选教训(201);报告未发布或已勾选过 409,文字过不了闸门 422。 */
    @PostMapping("/approve")
    public ResponseEntity<ResponseMessage<?>> approve(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postOrgMemoryApprove(authorization, body));
    }

    /** 退役一条组织记忆(不再召回,记录保留);已经退役 409。 */
    @PostMapping("/retire")
    public ResponseEntity<ResponseMessage<?>> retire(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postOrgMemoryRetire(authorization, body));
    }

    /** 续期:保质期改为从现在起 valid_days 天;已经退役 409。 */
    @PostMapping("/renew")
    public ResponseEntity<ResponseMessage<?>> renew(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postOrgMemoryRenew(authorization, body));
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
