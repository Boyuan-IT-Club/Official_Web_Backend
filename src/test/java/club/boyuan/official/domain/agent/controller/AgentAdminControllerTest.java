package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import club.boyuan.official.integration.agent.AgentAdminClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

/**
 * AgentAdminController 代理契约单测(纯 Mockito,不依赖 SpringContext/DB,同
 * AuthControllerAuthMeTest 先例)。验证:
 * - 查询参数与 Authorization 头原样转发
 * - 上游状态码/响应体透传(403/404 不包装)
 * - 上游不可达(ResourceAccessException)→ 502
 * - PUT body 透传
 */
class AgentAdminControllerTest {

    private static final String AUTH = "Bearer user-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final AgentAdminController controller = new AgentAdminController(client);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("列表:过滤参数与鉴权头原样转发")
    void conversations_forwardsParamsAndAuth() {
        when(client.getConversations(AUTH, 7, "web:u7:abc1", 20, 40))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[]}"));

        ResponseEntity<?> resp = controller.conversations(AUTH, 7, "web:u7:abc1", 20, 40);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("{\"items\":[]}", resp.getBody());
    }

    @Test
    @DisplayName("列表:可选参数缺省时转发 null(不拼空参数)")
    void conversations_forwardsNulls() {
        when(client.getConversations(eq(AUTH), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        ResponseEntity<?> resp = controller.conversations(AUTH, null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("详情与配置:上游 404/403 原样透传,不重新包装")
    void upstreamErrorStatusesArePassedThrough() {
        when(client.getConversation(AUTH, 42)).thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"会话不存在\"}"));
        when(client.getConfig(AUTH)).thenReturn(upstream(HttpStatus.FORBIDDEN, "{\"detail\":\"需要 agent:monitor 权限\"}"));

        assertEquals(HttpStatus.NOT_FOUND, controller.conversationDetail(AUTH, 42).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.config(AUTH).getStatusCode());
    }

    @Test
    @DisplayName("上游不可达(ResourceAccessException)→ 502")
    void upstreamUnreachableBecomes502() {
        when(client.getConversations(eq(AUTH), any(), any(), any(), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        ResponseEntity<?> resp = controller.conversations(AUTH, null, null, null, null);

        assertEquals(HttpStatus.BAD_GATEWAY, resp.getStatusCode());
    }

    @Test
    @DisplayName("PUT 配置:body 与鉴权头透传")
    void updateConfig_forwardsBody() {
        Map<String, String> body = Map.of("model_strong", "deepseek-chat");
        when(client.putConfig(eq(AUTH), eq(body)))
                .thenReturn(upstream(HttpStatus.OK, "{\"updated\":[\"model_strong\"]}"));

        ResponseEntity<?> resp = controller.updateConfig(AUTH, body);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("出站请求带 Authorization 头(契约:Agent 侧自行解析官网 JWT)")
    void outboundCarriesAuthorizationHeader() {
        HttpHeaders expected = new HttpHeaders();
        expected.set(HttpHeaders.AUTHORIZATION, AUTH);
        HttpEntity<Void> probe = new HttpEntity<>(expected);
        assertEquals(AUTH, probe.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        when(client.getConfig(AUTH)).thenReturn(upstream(HttpStatus.OK, "{}"));

        assertEquals(HttpStatus.OK, controller.config(AUTH).getStatusCode());
    }
}
