package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 组织记忆代理契约单测(同 AgentKbAdminControllerTest 先例:纯 Mockito,不依赖
 * SpringContext/DB)。验证:
 * - 查询参数/Authorization 头/请求体原样转发
 * - 上游状态码保持:201(已勾选)、404、409、422、503 都原样给前端,不抹平
 * - 上游不可达(ResourceAccessException)直调时上抛,由 @ExceptionHandler 转 502
 *
 * 权限面见 AgentProxySecurityTest;下游路径见 AgentAdminClientTest。
 */
class AgentOrgMemoryProxyControllerTest {

    private static final String AUTH = "Bearer admin-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentOrgMemoryProxyController controller =
            new AgentOrgMemoryProxyController(client, objectMapper);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("列表:status 与鉴权头原样转发,2xx 包 success 信封;缺省转发 null")
    void list_forwardsStatusAndWraps() throws Exception {
        when(client.getOrgMemories(AUTH, "approved"))
                .thenReturn(upstream(HttpStatus.OK, "{\"enabled\":false,\"items\":[],\"total\":0}"));
        when(client.getOrgMemories(eq(AUTH), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{\"enabled\":true,\"items\":[{\"memory_id\":\"org_1\"}],\"total\":1}"));

        ResponseEntity<ResponseMessage<?>> filtered = controller.list(AUTH, "approved");
        assertEquals(HttpStatus.OK, filtered.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"enabled\":false,\"items\":[],\"total\":0}"),
                filtered.getBody().getData());

        ResponseEntity<ResponseMessage<?>> all = controller.list(AUTH, null);
        assertEquals(
                objectMapper.readTree("{\"enabled\":true,\"items\":[{\"memory_id\":\"org_1\"}],\"total\":1}"),
                all.getBody().getData());
    }

    @Test
    @DisplayName("勾选:请求体原样转发;201 保持(不抹成 200),data 是组织记忆对象")
    void approve_forwardsBodyAndKeeps201() throws Exception {
        Map<String, Object> body = Map.of(
                "cycle_id", 2026, "version", 4, "lesson_index", 0,
                "content", "宣讲会要提前两周定场地", "audience", "member", "valid_days", 365);
        when(client.postOrgMemoryApprove(AUTH, body))
                .thenReturn(upstream(HttpStatus.CREATED, "{\"memory_id\":\"org_9\",\"status\":\"approved\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.approve(AUTH, body);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"memory_id\":\"org_9\",\"status\":\"approved\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("勾选:报告不存在 404、已勾选过 409、文字过不了闸门 422、审计写不进 503 各自保持")
    void approve_statusesArePassedThrough() {
        Map<String, Object> missing = Map.of("cycle_id", 2026, "version", 9, "lesson_index", 0);
        Map<String, Object> duplicate = Map.of("cycle_id", 2026, "version", 4, "lesson_index", 0);
        Map<String, Object> rejected = Map.of("cycle_id", 2026, "version", 4, "lesson_index", 1);
        Map<String, Object> auditDown = Map.of("cycle_id", 2026, "version", 4, "lesson_index", 2);
        when(client.postOrgMemoryApprove(AUTH, missing))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"指定版本的报告不存在\"}"));
        when(client.postOrgMemoryApprove(AUTH, duplicate))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"这条教训已经勾选过\"}"));
        when(client.postOrgMemoryApprove(AUTH, rejected))
                .thenReturn(upstream(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"内容疑似含个人信息\"}"));
        when(client.postOrgMemoryApprove(AUTH, auditDown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE,
                        "{\"detail\":\"审计服务不可用,操作未执行,请稍后重试\"}"));

        ResponseEntity<ResponseMessage<?>> notFound = controller.approve(AUTH, missing);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("指定版本的报告不存在", notFound.getBody().getMessage());
        assertNull(notFound.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.approve(AUTH, duplicate);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals(409, conflict.getBody().getCode());
        assertEquals("这条教训已经勾选过", conflict.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> gate = controller.approve(AUTH, rejected);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, gate.getStatusCode());
        assertEquals(422, gate.getBody().getCode());
        assertEquals("内容疑似含个人信息", gate.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> unavailable = controller.approve(AUTH, auditDown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("审计服务不可用,操作未执行,请稍后重试", unavailable.getBody().getMessage());
    }

    @Test
    @DisplayName("退役:请求体(memory_id)原样转发;不存在 404、已经退役 409")
    void retire_forwardsBodyAndKeepsStatuses() throws Exception {
        Map<String, Object> live = Map.of("memory_id", "org_9");
        Map<String, Object> gone = Map.of("memory_id", "org_missing");
        Map<String, Object> retired = Map.of("memory_id", "org_old");
        when(client.postOrgMemoryRetire(AUTH, live))
                .thenReturn(upstream(HttpStatus.OK, "{\"memory_id\":\"org_9\",\"status\":\"retired\"}"));
        when(client.postOrgMemoryRetire(AUTH, gone))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"组织记忆不存在\"}"));
        when(client.postOrgMemoryRetire(AUTH, retired))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"这条组织记忆已经退役\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.retire(AUTH, live);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"memory_id\":\"org_9\",\"status\":\"retired\"}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> notFound = controller.retire(AUTH, gone);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("组织记忆不存在", notFound.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> conflict = controller.retire(AUTH, retired);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("这条组织记忆已经退役", conflict.getBody().getMessage());
    }

    @Test
    @DisplayName("续期:请求体原样转发;已经退役 409、审计写不进 503")
    void renew_forwardsBodyAndKeepsStatuses() throws Exception {
        Map<String, Object> live = Map.of("memory_id", "org_9", "valid_days", 180);
        Map<String, Object> retired = Map.of("memory_id", "org_old");
        Map<String, Object> auditDown = Map.of("memory_id", "org_8");
        when(client.postOrgMemoryRenew(AUTH, live))
                .thenReturn(upstream(HttpStatus.OK, "{\"memory_id\":\"org_9\",\"expired\":false}"));
        when(client.postOrgMemoryRenew(AUTH, retired))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"这条组织记忆已经退役\"}"));
        when(client.postOrgMemoryRenew(AUTH, auditDown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE,
                        "{\"detail\":\"审计服务不可用,操作未执行,请稍后重试\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.renew(AUTH, live);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"memory_id\":\"org_9\",\"expired\":false}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.renew(AUTH, retired);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("这条组织记忆已经退役", conflict.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> unavailable = controller.renew(AUTH, auditDown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("审计服务不可用,操作未执行,请稍后重试", unavailable.getBody().getMessage());
    }

    @Test
    @DisplayName("Agent 侧 403(令牌权限不够):状态码保持,不被改写")
    void upstreamForbiddenIsPassedThrough() {
        when(client.getOrgMemories(AUTH, null))
                .thenReturn(upstream(HttpStatus.FORBIDDEN, "{\"detail\":\"需要 evaluation:run 权限\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.list(AUTH, null);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("需要 evaluation:run 权限", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("Agent 不可达:读、写接口直调时 ResourceAccessException 都上抛(由 @ExceptionHandler 转 502)")
    void unreachableExceptionPropagatesOnDirectCall() {
        Map<String, Object> body = Map.of("memory_id", "org_9");
        when(client.getOrgMemories(AUTH, null))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postOrgMemoryRetire(AUTH, body))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> controller.list(AUTH, null));
        assertThrows(ResourceAccessException.class, () -> controller.retire(AUTH, body));
    }

    @Test
    @DisplayName("502 handler 直调:BAD_GATEWAY + code 502")
    void unreachableHandlerReturns502() {
        ResponseEntity<ResponseMessage<Void>> handled = controller.handleUnreachable(
                new ResourceAccessException("Connection refused"));
        assertEquals(HttpStatus.BAD_GATEWAY, handled.getStatusCode());
        assertEquals(502, handled.getBody().getCode());
    }
}
