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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * 客服 Agent 知识库管理代理(RAG #134 R5,#121 决策:纯转发,不做管理逻辑)。
 *
 * 与 {@link AgentAdminController} 的差别只在权限码:知识库是**独立授权面**
 * kb:manage(V42 种子),可单独授予知识运营而无需 agent:monitor 的其它
 * 运营权限;Agent 侧 /admin/kb* 同码自校,双道闸。
 *
 * 响应信封/错误提取与 AgentAdminController 同约定(2xx 包 success 信封,
 * 4xx/5xx 提取 detail/message 转同状态码 error,连接失败转 502)。
 */
@RestController
@RequestMapping("/api/admin/agent/kb")
@PreAuthorize("hasAuthority('kb:manage')")
public class AgentKbAdminController {

    private static final Logger log = LoggerFactory.getLogger(AgentKbAdminController.class);

    private final AgentAdminClient agentAdminClient;
    private final ObjectMapper objectMapper;

    public AgentKbAdminController(AgentAdminClient agentAdminClient, ObjectMapper objectMapper) {
        this.agentAdminClient = agentAdminClient;
        this.objectMapper = objectMapper;
    }

    /** 知识条目分页列表(source_id/标题/kind/标签/启停/更新人/块数)。 */
    @GetMapping("/sources")
    public ResponseEntity<ResponseMessage<?>> sources(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return wrap(agentAdminClient.getKbSources(authorization, page, size, kind, keyword));
    }

    /** 新建并入库(分块+embedding)。 */
    @PostMapping("/sources")
    public ResponseEntity<ResponseMessage<?>> createSource(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.postKbSource(authorization, body));
    }

    /** 条目详情(含 faq/doc 内容与块数)。 */
    @GetMapping("/sources/{sourceId}")
    public ResponseEntity<ResponseMessage<?>> sourceDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sourceId) {
        return wrap(agentAdminClient.getKbSource(authorization, sourceId));
    }

    /** 更新并重嵌。 */
    @PutMapping("/sources/{sourceId}")
    public ResponseEntity<ResponseMessage<?>> updateSource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sourceId,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.putKbSource(authorization, sourceId, body));
    }

    /** 启/停用(停用立即退出生检索)。 */
    @PutMapping("/sources/{sourceId}/enabled")
    public ResponseEntity<ResponseMessage<?>> setSourceEnabled(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sourceId,
            @RequestBody Map<String, Object> body) {
        return wrap(agentAdminClient.putKbSourceEnabled(authorization, sourceId, body));
    }

    /** 删除(级联 chunks)。 */
    @DeleteMapping("/sources/{sourceId}")
    public ResponseEntity<ResponseMessage<?>> deleteSource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sourceId) {
        return wrap(agentAdminClient.deleteKbSource(authorization, sourceId));
    }

    /** 重嵌(按已存内容重建向量;换 embedding 模型后逐条补齐)。 */
    @PostMapping("/sources/{sourceId}/reembed")
    public ResponseEntity<ResponseMessage<?>> reembedSource(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String sourceId) {
        return wrap(agentAdminClient.postKbReembed(authorization, sourceId));
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

    /** Agent 不可达:502(同 AgentAdminController,直调控制器时异常上抛,见单测)。 */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ResponseMessage<Void>> handleUnreachable(ResourceAccessException e) {
        log.error("Agent 服务不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ResponseMessage.error(502, "Agent 服务不可达,请稍后重试"));
    }
}
