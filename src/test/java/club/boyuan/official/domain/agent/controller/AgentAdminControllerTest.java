package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.integration.agent.AgentAdminClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

/**
 * AgentAdminController 代理契约单测(纯 Mockito,不依赖 SpringContext/DB,同
 * AuthControllerAuthMeTest 先例)。验证:
 * - 查询参数与 Authorization 头原样转发
 * - 上游 2xx → 包 ResponseMessage.success(JsonNode)(信封契约:前端按 data 解包)
 * - 上游 4xx/5xx → 状态码保持 + error 信封,detail/message 提取为人话
 * - 上游不可达(ResourceAccessException)直调时上抛,由 @ExceptionHandler 转 502
 */
class AgentAdminControllerTest {

    private static final String AUTH = "Bearer user-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAdminController controller = new AgentAdminController(client, objectMapper);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("列表:过滤参数与鉴权头原样转发,2xx 包 success 信封")
    void conversations_forwardsParamsAndWrapsEnvelope() throws Exception {
        when(client.getConversations(AUTH, 7, "web:u7:abc1", 20, 40))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[{\"id\":1}],\"limit\":20}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.conversations(AUTH, 7, "web:u7:abc1", 20, 40);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ResponseMessage<?> body = resp.getBody();
        assertInstanceOf(ResponseMessage.class, body);
        assertEquals(objectMapper.readTree("{\"items\":[{\"id\":1}],\"limit\":20}"), body.getData());
    }

    @Test
    @DisplayName("列表:可选参数缺省时转发 null(不拼空参数)")
    void conversations_forwardsNulls() {
        when(client.getConversations(eq(AUTH), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        assertEquals(HttpStatus.OK, controller.conversations(AUTH, null, null, null, null).getStatusCode());
    }

    @Test
    @DisplayName("上游 404/403:状态码保持,error 信封带 detail 人话文案")
    void upstreamErrorStatusesArePassedThroughWithDetail() {
        when(client.getConversation(AUTH, 42))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"会话不存在\"}"));
        when(client.getConfig(AUTH))
                .thenReturn(upstream(HttpStatus.FORBIDDEN, "{\"detail\":\"需要 agent:monitor 权限\"}"));

        ResponseEntity<ResponseMessage<?>> notFound = controller.conversationDetail(AUTH, 42);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("会话不存在", notFound.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> forbidden = controller.config(AUTH);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("需要 agent:monitor 权限", forbidden.getBody().getMessage());
    }

    @Test
    @DisplayName("上游不可达(ResourceAccessException)直调时上抛,@ExceptionHandler 转 502")
    void upstreamUnreachableBecomes502ViaExceptionHandler() {
        when(client.getConversations(eq(AUTH), eq(null), eq(null), eq(null), eq(null)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class,
                () -> controller.conversations(AUTH, null, null, null, null));

        ResponseEntity<ResponseMessage<Void>> handled = controller.handleUnreachable(
                new ResourceAccessException("Connection refused"));
        assertEquals(HttpStatus.BAD_GATEWAY, handled.getStatusCode());
        assertEquals(502, handled.getBody().getCode());
    }

    @Test
    @DisplayName("PUT 配置:body 与鉴权头透传,updated 列表在 data 内")
    void updateConfig_forwardsBody() throws Exception {
        Map<String, String> body = Map.of("model_strong", "deepseek-chat");
        when(client.putConfig(eq(AUTH), eq(body)))
                .thenReturn(upstream(HttpStatus.OK, "{\"updated\":[\"model_strong\"]}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.updateConfig(AUTH, body);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(objectMapper.readTree("{\"updated\":[\"model_strong\"]}"), resp.getBody().getData());
    }
}
