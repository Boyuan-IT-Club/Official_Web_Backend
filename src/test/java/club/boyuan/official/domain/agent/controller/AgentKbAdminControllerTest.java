package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * AgentKbAdminController 代理契约单测(RAG #134 R5,同 AgentAdminControllerTest
 * 先例:纯 Mockito,不依赖 SpringContext/DB)。验证:
 * - 查询参数/路径变量/Authorization 头/请求体原样转发
 * - 上游 2xx → success 信封;4xx/5xx 状态码保持 + detail 人话
 * - 上游不可达(ResourceAccessException)直调时上抛,由 @ExceptionHandler 转 502
 *
 * 权限面:类级 @PreAuthorize(kb:manage) 由 Spring Security 表达式拦截,
 * 不在本单测范围(纯控制器契约),同 AgentAdminControllerTest 的取舍。
 */
class AgentKbAdminControllerTest {

    private static final String AUTH = "Bearer user-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentKbAdminController controller = new AgentKbAdminController(client, objectMapper);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("列表:分页/过滤参数与鉴权头原样转发,2xx 包 success 信封")
    void sources_forwardsParamsAndWrapsEnvelope() throws Exception {
        when(client.getKbSources(AUTH, 2, 10, "test", "评测"))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.sources(AUTH, 2, 10, "test", "评测");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"items\":[],\"total\":0}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("列表:可选参数缺省时转发 null(不拼空参数)")
    void sources_forwardsNulls() {
        when(client.getKbSources(eq(AUTH), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        assertEquals(HttpStatus.OK, controller.sources(AUTH, null, null, null, null).getStatusCode());
    }

    @Test
    @DisplayName("新建:请求体 Map 原样转发,201 信封带回 source_id")
    void createSource_forwardsBody() throws Exception {
        Map<String, Object> body = Map.of("title", "技术部", "type", "doc", "content_md", "# 技术部");
        when(client.postKbSource(AUTH, body))
                .thenReturn(upstream(HttpStatus.CREATED, "{\"source_id\":\"kb_abc\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.createSource(AUTH, body);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"source_id\":\"kb_abc\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("更新/启停/删除/重嵌:路径变量 sourceId 原样转发")
    void pathVariablesAreForwarded() {
        Map<String, Object> body = Map.of("title", "新", "type", "doc", "content_md", "x");
        when(client.putKbSource(AUTH, "kb_1", body)).thenReturn(upstream(HttpStatus.OK, "{}"));
        when(client.putKbSourceEnabled(AUTH, "kb_1", Map.of("enabled", false)))
                .thenReturn(upstream(HttpStatus.OK, "{}"));
        when(client.deleteKbSource(AUTH, "kb_1")).thenReturn(upstream(HttpStatus.OK, "{}"));
        when(client.postKbReembed(AUTH, "kb_1")).thenReturn(upstream(HttpStatus.OK, "{}"));

        assertEquals(HttpStatus.OK, controller.updateSource(AUTH, "kb_1", body).getStatusCode());
        assertEquals(HttpStatus.OK, controller.setSourceEnabled(AUTH, "kb_1", Map.of("enabled", false)).getStatusCode());
        assertEquals(HttpStatus.OK, controller.deleteSource(AUTH, "kb_1").getStatusCode());
        assertEquals(HttpStatus.OK, controller.reembedSource(AUTH, "kb_1").getStatusCode());
    }

    @Test
    @DisplayName("上游 403/404:状态码保持,error 信封带 detail 人话")
    void upstreamErrorStatusesArePassedThroughWithDetail() {
        when(client.getKbSource(AUTH, "kb_missing"))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"条目不存在\"}"));
        when(client.postKbSource(AUTH, Map.of()))
                .thenReturn(upstream(HttpStatus.FORBIDDEN, "{\"detail\":\"需要 kb:manage 权限\"}"));

        ResponseEntity<ResponseMessage<?>> notFound = controller.sourceDetail(AUTH, "kb_missing");
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("条目不存在", notFound.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> forbidden = controller.createSource(AUTH, Map.of());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("需要 kb:manage 权限", forbidden.getBody().getMessage());
    }

    @Test
    @DisplayName("Agent 不可达:直调控制器时 ResourceAccessException 上抛(由 @ExceptionHandler 转 502)")
    void unreachableExceptionPropagatesOnDirectCall() {
        when(client.getKbSources(AUTH, null, null, null, null))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(
                ResourceAccessException.class,
                () -> controller.sources(AUTH, null, null, null, null));
    }

    @Test
    @DisplayName("502 handler 直调:BAD_GATEWAY + code 502(评审 P2 补,与先例对齐)")
    void unreachableHandlerReturns502() {
        ResponseEntity<ResponseMessage<Void>> handled = controller.handleUnreachable(
                new ResourceAccessException("Connection refused"));
        assertEquals(HttpStatus.BAD_GATEWAY, handled.getStatusCode());
        assertEquals(502, handled.getBody().getCode());
    }
}
