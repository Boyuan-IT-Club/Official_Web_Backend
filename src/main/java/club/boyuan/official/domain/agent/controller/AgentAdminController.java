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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * 客服 Agent 管理代理(M6 #115,#103 决策:Backend 不做管理 API,只转发)。
 *
 * 三块面板的数据源:运营(会话列表/轮次详情)、配置(掩码回显/低敏热生效)。
 * 权限双道闸:此处 @PreAuthorize(agent:monitor,种子 V37)+ Agent 侧
 * 用官网 JWT 自行解析再校验同码。
 *
 * 响应信封:上游 2xx 包成站内统一 ResponseMessage.success(data)(前端
 * request.ts 拦截器按此解包);上游 4xx/5xx 提取 detail/message 转同状态码的
 * ResponseMessage.error(前端 axios reject 读 message);连接失败转 502。
 */
@RestController
@RequestMapping("/api/admin/agent")
@PreAuthorize("hasAuthority('agent:monitor')")
public class AgentAdminController {

    private static final Logger log = LoggerFactory.getLogger(AgentAdminController.class);

    private final AgentAdminClient agentAdminClient;
    private final ObjectMapper objectMapper;

    public AgentAdminController(AgentAdminClient agentAdminClient, ObjectMapper objectMapper) {
        this.agentAdminClient = agentAdminClient;
        this.objectMapper = objectMapper;
    }

    /** 运营列表:时间/用户/问题首字/状态,支持 user_id/thread_id 过滤 + 分页。 */
    @GetMapping("/conversations")
    public ResponseEntity<ResponseMessage<?>> conversations(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "thread_id", required = false) String threadId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return wrap(agentAdminClient.getConversations(authorization, userId, threadId, limit, offset));
    }

    /** 轮次详情:工具/耗时/错误码 + 可展开回复摘要。 */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ResponseMessage<?>> conversationDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long conversationId) {
        return wrap(agentAdminClient.getConversation(authorization, conversationId));
    }

    /** 配置回显:低敏键实值 + 高敏键 {configured, masked}。 */
    @GetMapping("/config")
    public ResponseEntity<ResponseMessage<?>> config(@RequestHeader("Authorization") String authorization) {
        return wrap(agentAdminClient.getConfig(authorization));
    }

    /** 改低敏配置(HOT_KEYS 白名单校验在 Agent 侧,热生效)。 */
    @PutMapping("/config")
    public ResponseEntity<ResponseMessage<?>> updateConfig(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {
        return wrap(agentAdminClient.putConfig(authorization, body));
    }

    /** 上游响应 → 站内统一信封(状态码保持,前端按既有约定解包/报错)。 */
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

    /** Agent 不可达:502(@ExceptionHandler 仅经 DispatcherServlet 生效;直调控制器时异常上抛,见单测)。 */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ResponseMessage<Void>> handleUnreachable(ResourceAccessException e) {
        log.error("Agent 服务不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ResponseMessage.error(502, "Agent 服务不可达,请稍后重试"));
    }
}
