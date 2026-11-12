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
 * 周期复盘代理契约单测(同 AgentKbAdminControllerTest 先例:纯 Mockito,不依赖
 * SpringContext/DB)。验证:
 * - 查询参数/Authorization 头/请求体原样转发
 * - 上游状态码保持:202(已开始生成)、404、409、503 都原样给前端,不抹平
 * - 上游不可达(ResourceAccessException)直调时上抛,由 @ExceptionHandler 转 502
 *
 * 权限面见 AgentProxySecurityTest;下游路径与编码见 AgentAdminClientTest。
 */
class AgentRetrospectiveProxyControllerTest {

    private static final String AUTH = "Bearer admin-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRetrospectiveProxyController controller =
            new AgentRetrospectiveProxyController(client, objectMapper);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("采集快照:请求体与鉴权头原样转发,2xx 包 success 信封")
    void takeSnapshot_forwardsBodyAndWraps() throws Exception {
        Map<String, Object> body = Map.of("cycle_id", 2026);
        when(client.postRetrospectiveSnapshot(AUTH, body))
                .thenReturn(upstream(HttpStatus.OK, "{\"cycle_id\":2026,\"version\":3,\"created\":false}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.takeSnapshot(AUTH, body);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"cycle_id\":2026,\"version\":3,\"created\":false}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("采集快照:周期不存在 404,detail 人话透传")
    void takeSnapshot_cycleNotFound() {
        Map<String, Object> body = Map.of("cycle_id", 9999);
        when(client.postRetrospectiveSnapshot(AUTH, body))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"招新周期 9999 不存在\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.takeSnapshot(AUTH, body);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(404, resp.getBody().getCode());
        assertEquals("招新周期 9999 不存在", resp.getBody().getMessage());
        assertNull(resp.getBody().getData());
    }

    @Test
    @DisplayName("读快照:cycle_id/version/baseline_cycle_id 原样转发")
    void snapshot_forwardsParams() throws Exception {
        when(client.getRetrospectiveSnapshot(AUTH, 2026, 2, 2025))
                .thenReturn(upstream(HttpStatus.OK, "{\"cycle_id\":2026,\"version\":2,\"snapshot\":{}}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.snapshot(AUTH, 2026, 2, 2025);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"cycle_id\":2026,\"version\":2,\"snapshot\":{}}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("读快照:可选参数缺省时转发 null(不拼空参数)")
    void snapshot_forwardsNulls() {
        when(client.getRetrospectiveSnapshot(eq(AUTH), eq(2026), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        assertEquals(HttpStatus.OK, controller.snapshot(AUTH, 2026, null, null).getStatusCode());
    }

    @Test
    @DisplayName("读快照:还没有快照 404、基线指标版本不同 409、基线即本周期 400——状态码各自保持")
    void snapshot_statusesArePassedThrough() {
        when(client.getRetrospectiveSnapshot(AUTH, 2026, null, null))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"该周期还没有指标快照\"}"));
        when(client.getRetrospectiveSnapshot(AUTH, 2026, null, 2024))
                .thenReturn(upstream(HttpStatus.CONFLICT,
                        "{\"detail\":\"基线周期没有 cycle_metrics/v1 版指标定义的快照,不能对比\"}"));
        when(client.getRetrospectiveSnapshot(AUTH, 2026, null, 2026))
                .thenReturn(upstream(HttpStatus.BAD_REQUEST, "{\"detail\":\"基线周期不能是本周期\"}"));

        ResponseEntity<ResponseMessage<?>> missing = controller.snapshot(AUTH, 2026, null, null);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("该周期还没有指标快照", missing.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> mismatch = controller.snapshot(AUTH, 2026, null, 2024);
        assertEquals(HttpStatus.CONFLICT, mismatch.getStatusCode());
        assertEquals(409, mismatch.getBody().getCode());
        assertEquals("基线周期没有 cycle_metrics/v1 版指标定义的快照,不能对比", mismatch.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> same = controller.snapshot(AUTH, 2026, null, 2026);
        assertEquals(HttpStatus.BAD_REQUEST, same.getStatusCode());
        assertEquals("基线周期不能是本周期", same.getBody().getMessage());
    }

    @Test
    @DisplayName("快照版本列表:cycle_id 转发,2xx 包信封")
    void snapshots_forwardsAndWraps() throws Exception {
        when(client.getRetrospectiveSnapshots(AUTH, 2026))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.snapshots(AUTH, 2026);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(objectMapper.readTree("{\"items\":[],\"total\":0}"), resp.getBody().getData());
    }

    @Test
    @DisplayName("生成报告:请求体转发;202 保持(不抹成 200),data 带回占到的版本号")
    void runReport_keeps202() throws Exception {
        Map<String, Object> body = Map.of("cycle_id", 2026, "baseline_cycle_id", 2025);
        when(client.postRetrospectiveRun(AUTH, body))
                .thenReturn(upstream(HttpStatus.ACCEPTED,
                        "{\"cycle_id\":2026,\"version\":4,\"status\":\"generating\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.runReport(AUTH, body);

        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"cycle_id\":2026,\"version\":4,\"status\":\"generating\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("生成报告:已有一份在生成 409、功能未开启 503——前端靠状态码区分,各自保持")
    void runReport_conflictAndDisabledArePassedThrough() {
        Map<String, Object> busy = Map.of("cycle_id", 2026);
        Map<String, Object> disabled = Map.of("cycle_id", 2027);
        when(client.postRetrospectiveRun(AUTH, busy))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"该周期已有一份报告在生成\"}"));
        when(client.postRetrospectiveRun(AUTH, disabled))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE, "{\"detail\":\"复盘报告生成尚未开启\"}"));

        ResponseEntity<ResponseMessage<?>> conflict = controller.runReport(AUTH, busy);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("该周期已有一份报告在生成", conflict.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> off = controller.runReport(AUTH, disabled);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, off.getStatusCode());
        assertEquals(503, off.getBody().getCode());
        assertEquals("复盘报告生成尚未开启", off.getBody().getMessage());
    }

    @Test
    @DisplayName("读报告:cycle_id/version 转发;没有可见版本 404")
    void report_forwardsAndKeeps404() throws Exception {
        when(client.getRetrospectiveReport(AUTH, 2026, 4))
                .thenReturn(upstream(HttpStatus.OK, "{\"version\":4,\"status\":\"draft\"}"));
        when(client.getRetrospectiveReport(eq(AUTH), eq(2025), isNull()))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"该周期还没有已发布的报告\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.report(AUTH, 2026, 4);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(objectMapper.readTree("{\"version\":4,\"status\":\"draft\"}"), ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> missing = controller.report(AUTH, 2025, null);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("该周期还没有已发布的报告", missing.getBody().getMessage());
    }

    @Test
    @DisplayName("报告版本列表:cycle_id 转发,2xx 包信封")
    void reports_forwardsAndWraps() throws Exception {
        when(client.getRetrospectiveReports(AUTH, 2026))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[{\"version\":4}],\"total\":1}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.reports(AUTH, 2026);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"items\":[{\"version\":4}],\"total\":1}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("发布报告:请求体转发;不是草稿 409、审计写不进 503(未发布)各自保持")
    void publishReport_forwardsAndKeepsStatuses() throws Exception {
        Map<String, Object> draft = Map.of("cycle_id", 2026, "version", 4);
        Map<String, Object> published = Map.of("cycle_id", 2026, "version", 3);
        Map<String, Object> auditDown = Map.of("cycle_id", 2026, "version", 5);
        when(client.postRetrospectivePublish(AUTH, draft))
                .thenReturn(upstream(HttpStatus.OK, "{\"version\":4,\"status\":\"published\"}"));
        when(client.postRetrospectivePublish(AUTH, published))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"该版本不是草稿\"}"));
        when(client.postRetrospectivePublish(AUTH, auditDown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE,
                        "{\"detail\":\"审计服务不可用,报告未发布,请稍后重试\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.publishReport(AUTH, draft);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"version\":4,\"status\":\"published\"}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.publishReport(AUTH, published);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("该版本不是草稿", conflict.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> unavailable = controller.publishReport(AUTH, auditDown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("审计服务不可用,报告未发布,请稍后重试", unavailable.getBody().getMessage());
    }

    @Test
    @DisplayName("Agent 侧 403(令牌权限不够):状态码保持,不被改写")
    void upstreamForbiddenIsPassedThrough() {
        when(client.getRetrospectiveSnapshots(AUTH, 2026))
                .thenReturn(upstream(HttpStatus.FORBIDDEN, "{\"detail\":\"需要 resume:audit 或 evaluation:run 权限\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.snapshots(AUTH, 2026);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("需要 resume:audit 或 evaluation:run 权限", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("Agent 不可达:读、写接口直调时 ResourceAccessException 都上抛(由 @ExceptionHandler 转 502)")
    void unreachableExceptionPropagatesOnDirectCall() {
        Map<String, Object> body = Map.of("cycle_id", 2026, "version", 4);
        when(client.getRetrospectiveSnapshots(AUTH, 2026))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postRetrospectivePublish(AUTH, body))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> controller.snapshots(AUTH, 2026));
        assertThrows(ResourceAccessException.class, () -> controller.publishReport(AUTH, body));
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
