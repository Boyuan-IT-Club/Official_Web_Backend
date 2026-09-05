package club.boyuan.official.domain.agent.controller;

import java.util.Map;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.integration.agent.AgentAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 用官网 JWT 自行解析再校验同码。上游错误状态原样透传,不可达转 502。
 */
@RestController
@RequestMapping("/api/admin/agent")
@PreAuthorize("hasAuthority('agent:monitor')")
public class AgentAdminController {

    private static final Logger log = LoggerFactory.getLogger(AgentAdminController.class);

    private final AgentAdminClient agentAdminClient;

    public AgentAdminController(AgentAdminClient agentAdminClient) {
        this.agentAdminClient = agentAdminClient;
    }

    /** 运营列表:时间/用户/问题首字/状态,支持 user_id/thread_id 过滤 + 分页。 */
    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "thread_id", required = false) String threadId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        try {
            return agentAdminClient.getConversations(authorization, userId, threadId, limit, offset);
        } catch (ResourceAccessException e) {
            return unavailable(e);
        }
    }

    /** 轮次详情:工具/耗时/错误码 + 可展开回复摘要。 */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<?> conversationDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable long conversationId) {
        try {
            return agentAdminClient.getConversation(authorization, conversationId);
        } catch (ResourceAccessException e) {
            return unavailable(e);
        }
    }

    /** 配置回显:低敏键实值 + 高敏键 {configured, masked}。 */
    @GetMapping("/config")
    public ResponseEntity<?> config(@RequestHeader("Authorization") String authorization) {
        try {
            return agentAdminClient.getConfig(authorization);
        } catch (ResourceAccessException e) {
            return unavailable(e);
        }
    }

    /** 改低敏配置(HOT_KEYS 白名单校验在 Agent 侧,热生效)。 */
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {
        try {
            return agentAdminClient.putConfig(authorization, body);
        } catch (ResourceAccessException e) {
            return unavailable(e);
        }
    }

    private ResponseEntity<ResponseMessage<Void>> unavailable(ResourceAccessException e) {
        log.error("Agent 服务不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ResponseMessage.error(502, "Agent 服务不可达,请稍后重试"));
    }
}
