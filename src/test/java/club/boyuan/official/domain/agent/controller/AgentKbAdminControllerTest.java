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

    // ── 知识库运营(/ops/**)──

    @Test
    @DisplayName("启动分析:请求体转发;202 保持(不抹成 200),data 带回 run_id")
    void startOpsRun_keeps202() throws Exception {
        Map<String, Object> body = Map.of("days", 14);
        when(client.postKbOpsRun(AUTH, body))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"r1\",\"status\":\"running\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.startOpsRun(AUTH, body);

        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"run_id\":\"r1\",\"status\":\"running\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("启动分析:不带请求体时转发 null(Agent 取默认天数)")
    void startOpsRun_forwardsMissingBody() {
        when(client.postKbOpsRun(eq(AUTH), isNull()))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"r2\",\"status\":\"running\"}"));

        assertEquals(HttpStatus.ACCEPTED, controller.startOpsRun(AUTH, null).getStatusCode());
    }

    @Test
    @DisplayName("启动分析:已有分析在跑 409,状态码保持 + detail 人话")
    void startOpsRun_conflictIsPassedThrough() {
        Map<String, Object> body = Map.of();
        when(client.postKbOpsRun(AUTH, body))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"已有一次分析在运行\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.startOpsRun(AUTH, body);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getCode());
        assertEquals("已有一次分析在运行", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("运行列表/主题清单:limit、run_id、scope 原样转发,缺省转发 null")
    void opsRunsAndTopics_forwardParams() throws Exception {
        when(client.getKbOpsRuns(AUTH, 20))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0}"));
        when(client.getKbOpsRuns(eq(AUTH), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));
        when(client.getKbOpsTopics(AUTH, "r1", "all"))
                .thenReturn(upstream(HttpStatus.OK, "{\"run\":{\"run_id\":\"r1\"},\"items\":[],\"total\":0}"));
        when(client.getKbOpsTopics(eq(AUTH), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{\"run\":null,\"items\":[],\"total\":0}"));

        ResponseEntity<ResponseMessage<?>> runs = controller.opsRuns(AUTH, 20);
        assertEquals(HttpStatus.OK, runs.getStatusCode());
        assertEquals(objectMapper.readTree("{\"items\":[],\"total\":0}"), runs.getBody().getData());
        assertEquals(HttpStatus.OK, controller.opsRuns(AUTH, null).getStatusCode());

        ResponseEntity<ResponseMessage<?>> topics = controller.opsTopics(AUTH, "r1", "all");
        assertEquals(HttpStatus.OK, topics.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"run\":{\"run_id\":\"r1\"},\"items\":[],\"total\":0}"),
                topics.getBody().getData());
        assertEquals(
                objectMapper.readTree("{\"run\":null,\"items\":[],\"total\":0}"),
                controller.opsTopics(AUTH, null, null).getBody().getData());
    }

    @Test
    @DisplayName("主题清单:运行不存在 404、scope 无效 400,状态码保持")
    void opsTopics_errorsArePassedThrough() {
        when(client.getKbOpsTopics(AUTH, "nope", null))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"运行不存在\"}"));
        when(client.getKbOpsTopics(AUTH, null, "bogus"))
                .thenReturn(upstream(HttpStatus.BAD_REQUEST, "{\"detail\":\"scope 取值无效\"}"));

        ResponseEntity<ResponseMessage<?>> notFound = controller.opsTopics(AUTH, "nope", null);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("运行不存在", notFound.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> badScope = controller.opsTopics(AUTH, null, "bogus");
        assertEquals(HttpStatus.BAD_REQUEST, badScope.getStatusCode());
        assertEquals("scope 取值无效", badScope.getBody().getMessage());
    }

    @Test
    @DisplayName("草稿列表:status/kind/page/size 原样转发,缺省转发 null")
    void opsDrafts_forwardParams() throws Exception {
        when(client.getKbOpsDrafts(AUTH, "all", "new_faq", 2, 50))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0}"));
        when(client.getKbOpsDrafts(eq(AUTH), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.opsDrafts(AUTH, "all", "new_faq", 2, 50);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(objectMapper.readTree("{\"items\":[],\"total\":0}"), resp.getBody().getData());
        assertEquals(HttpStatus.OK, controller.opsDrafts(AUTH, null, null, null, null).getStatusCode());
    }

    @Test
    @DisplayName("草稿详情:草稿号转发;不存在 404")
    void opsDraftDetail_forwardsIdAndKeeps404() throws Exception {
        when(client.getKbOpsDraft(AUTH, 12L))
                .thenReturn(upstream(HttpStatus.OK, "{\"draft_id\":12,\"status\":\"pending\"}"));
        when(client.getKbOpsDraft(AUTH, 404L))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"草稿不存在\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.opsDraftDetail(AUTH, 12L);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"draft_id\":12,\"status\":\"pending\"}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> missing = controller.opsDraftDetail(AUTH, 404L);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("草稿不存在", missing.getBody().getMessage());
    }

    @Test
    @DisplayName("修改草稿:草稿号与请求体原样转发;已处理 409")
    void editOpsDraft_forwardsIdAndBody() throws Exception {
        Map<String, Object> body = Map.of("question", "社费多少", "answer", "每学期 20 元");
        when(client.putKbOpsDraft(AUTH, 12L, body))
                .thenReturn(upstream(HttpStatus.OK, "{\"draft_id\":12,\"status\":\"pending\"}"));
        when(client.putKbOpsDraft(AUTH, 13L, body))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"草稿已处理(当前状态 approved)\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.editOpsDraft(AUTH, 12L, body);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"draft_id\":12,\"status\":\"pending\"}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.editOpsDraft(AUTH, 13L, body);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("草稿已处理(当前状态 approved)", conflict.getBody().getMessage());
    }

    @Test
    @DisplayName("批准草稿:草稿号转发;正在被处理 409、审计写不进 503、嵌入失败 502 各自保持")
    void approveOpsDraft_forwardsIdAndKeepsStatuses() throws Exception {
        when(client.postKbOpsDraftApprove(AUTH, 12L))
                .thenReturn(upstream(HttpStatus.OK,
                        "{\"draft_id\":12,\"status\":\"approved\",\"published_source_id\":\"kb_9\",\"audit_recorded\":true}"));
        when(client.postKbOpsDraftApprove(AUTH, 13L))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"这份草稿正在被处理,请稍后刷新\"}"));
        when(client.postKbOpsDraftApprove(AUTH, 14L))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE,
                        "{\"detail\":\"审计服务不可用,草稿未发布,请稍后重试\"}"));
        when(client.postKbOpsDraftApprove(AUTH, 15L))
                .thenReturn(upstream(HttpStatus.BAD_GATEWAY, "{\"detail\":\"嵌入服务调用失败\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.approveOpsDraft(AUTH, 12L);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree(
                        "{\"draft_id\":12,\"status\":\"approved\",\"published_source_id\":\"kb_9\",\"audit_recorded\":true}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.approveOpsDraft(AUTH, 13L);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("这份草稿正在被处理,请稍后刷新", conflict.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> auditDown = controller.approveOpsDraft(AUTH, 14L);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, auditDown.getStatusCode());
        assertEquals("审计服务不可用,草稿未发布,请稍后重试", auditDown.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> embedding = controller.approveOpsDraft(AUTH, 15L);
        assertEquals(HttpStatus.BAD_GATEWAY, embedding.getStatusCode());
        assertEquals("嵌入服务调用失败", embedding.getBody().getMessage());
    }

    @Test
    @DisplayName("批准草稿失败:Agent 的 detail 原样进 message——前端靠它说明「知识库有没有被改动」")
    void approveOpsDraft_detailIsCopiedVerbatimIntoMessage() {
        String notChanged = "发布失败,知识库未改动,请稍后重试";
        String alreadyWritten = "内容已写入知识库(条目 kb_draft17),但草稿状态更新失败;请重试批准,重试不会重复发布";
        String incomplete = "「待补充」草稿还没有写答案,不能发布";
        when(client.postKbOpsDraftApprove(AUTH, 21L))
                .thenReturn(upstream(HttpStatus.INTERNAL_SERVER_ERROR, "{\"detail\":\"" + notChanged + "\"}"));
        when(client.postKbOpsDraftApprove(AUTH, 22L))
                .thenReturn(upstream(HttpStatus.INTERNAL_SERVER_ERROR, "{\"detail\":\"" + alreadyWritten + "\"}"));
        when(client.postKbOpsDraftApprove(AUTH, 23L))
                .thenReturn(upstream(HttpStatus.BAD_REQUEST, "{\"detail\":\"" + incomplete + "\"}"));

        ResponseEntity<ResponseMessage<?>> untouched = controller.approveOpsDraft(AUTH, 21L);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, untouched.getStatusCode());
        assertEquals(notChanged, untouched.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> written = controller.approveOpsDraft(AUTH, 22L);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, written.getStatusCode());
        assertEquals(500, written.getBody().getCode());
        assertEquals(alreadyWritten, written.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> bad = controller.approveOpsDraft(AUTH, 23L);
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
        assertEquals(incomplete, bad.getBody().getMessage());
    }

    @Test
    @DisplayName("驳回草稿:草稿号转发;已被处理 409")
    void rejectOpsDraft_forwardsIdAndKeeps409() throws Exception {
        when(client.postKbOpsDraftReject(AUTH, 12L))
                .thenReturn(upstream(HttpStatus.OK, "{\"draft_id\":12,\"status\":\"rejected\"}"));
        when(client.postKbOpsDraftReject(AUTH, 13L))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"草稿已被处理\"}"));

        ResponseEntity<ResponseMessage<?>> ok = controller.rejectOpsDraft(AUTH, 12L);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"draft_id\":12,\"status\":\"rejected\"}"),
                ok.getBody().getData());

        ResponseEntity<ResponseMessage<?>> conflict = controller.rejectOpsDraft(AUTH, 13L);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("草稿已被处理", conflict.getBody().getMessage());
    }

    @Test
    @DisplayName("知识库运营:Agent 不可达时读、写接口都上抛 ResourceAccessException")
    void opsUnreachableExceptionPropagates() {
        when(client.getKbOpsTopics(AUTH, null, null))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postKbOpsDraftApprove(AUTH, 12L))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> controller.opsTopics(AUTH, null, null));
        assertThrows(ResourceAccessException.class, () -> controller.approveOpsDraft(AUTH, 12L));
    }

    // ── 忠实度抽样(/ops/faithfulness/**)──

    @Test
    @DisplayName("启动抽样:请求体转发,202 保持;不带请求体时转发 null")
    void startFaithfulnessRun_keeps202() throws Exception {
        Map<String, Object> body = Map.of("days", 14, "sample_size", 50);
        when(client.postKbFaithfulnessRun(AUTH, body))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"kbfaith_1\",\"status\":\"running\"}"));
        when(client.postKbFaithfulnessRun(eq(AUTH), isNull()))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"kbfaith_2\",\"status\":\"running\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.startFaithfulnessRun(AUTH, body);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"run_id\":\"kbfaith_1\",\"status\":\"running\"}"),
                resp.getBody().getData());

        assertEquals(HttpStatus.ACCEPTED, controller.startFaithfulnessRun(AUTH, null).getStatusCode());
    }

    @Test
    @DisplayName("启动抽样:功能未启用 / 已有抽样在跑都是 409,状态码保持,靠 detail 区分")
    void startFaithfulnessRun_conflictIsPassedThrough() {
        Map<String, Object> disabled = Map.of("days", 7);
        Map<String, Object> busy = Map.of("days", 8);
        when(client.postKbFaithfulnessRun(AUTH, disabled))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"忠实度抽样没有在配置里打开\"}"));
        when(client.postKbFaithfulnessRun(AUTH, busy))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"已有一次抽样在运行\"}"));

        ResponseEntity<ResponseMessage<?>> off = controller.startFaithfulnessRun(AUTH, disabled);
        assertEquals(HttpStatus.CONFLICT, off.getStatusCode());
        assertEquals(409, off.getBody().getCode());
        assertEquals("忠实度抽样没有在配置里打开", off.getBody().getMessage());

        ResponseEntity<ResponseMessage<?>> running = controller.startFaithfulnessRun(AUTH, busy);
        assertEquals(HttpStatus.CONFLICT, running.getStatusCode());
        assertEquals("已有一次抽样在运行", running.getBody().getMessage());
    }

    @Test
    @DisplayName("抽样运行列表:limit 转发,缺省转发 null")
    void faithfulnessRuns_forwardLimit() throws Exception {
        when(client.getKbFaithfulnessRuns(AUTH, 20))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0}"));
        when(client.getKbFaithfulnessRuns(eq(AUTH), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.faithfulnessRuns(AUTH, 20);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(objectMapper.readTree("{\"items\":[],\"total\":0}"), resp.getBody().getData());
        assertEquals(HttpStatus.OK, controller.faithfulnessRuns(AUTH, null).getStatusCode());
    }

    @Test
    @DisplayName("抽样样本:run_id/problems_only 转发,缺省转发 null;响应体原样进 data;运行不存在 404")
    void faithfulnessSamples_forwardParamsAndKeep404() throws Exception {
        String samples = "{\"run\":{\"run_id\":\"kbfaith_1\"},\"items\":[{\"sample_id\":3,"
                + "\"user_message\":\"入社要交钱吗\",\"reply_summary\":\"要缴纳会费。\",\"contradicted\":1}],\"total\":1}";
        when(client.getKbFaithfulnessSamples(AUTH, "kbfaith_1", true))
                .thenReturn(upstream(HttpStatus.OK, samples));
        when(client.getKbFaithfulnessSamples(eq(AUTH), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{\"run\":null,\"items\":[],\"total\":0}"));
        when(client.getKbFaithfulnessSamples(AUTH, "nope", null))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"运行不存在\"}"));

        ResponseEntity<ResponseMessage<?>> resp = controller.faithfulnessSamples(AUTH, "kbfaith_1", true);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(objectMapper.readTree(samples), resp.getBody().getData());

        assertEquals(
                objectMapper.readTree("{\"run\":null,\"items\":[],\"total\":0}"),
                controller.faithfulnessSamples(AUTH, null, null).getBody().getData());

        ResponseEntity<ResponseMessage<?>> missing = controller.faithfulnessSamples(AUTH, "nope", null);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("运行不存在", missing.getBody().getMessage());
    }

    @Test
    @DisplayName("忠实度抽样:Agent 不可达时读、写接口都上抛 ResourceAccessException")
    void faithfulnessUnreachableExceptionPropagates() {
        when(client.getKbFaithfulnessSamples(AUTH, null, null))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postKbFaithfulnessRun(AUTH, null))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> controller.faithfulnessSamples(AUTH, null, null));
        assertThrows(ResourceAccessException.class, () -> controller.startFaithfulnessRun(AUTH, null));
    }
}
