package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import club.boyuan.official.common.exception.GlobalExceptionHandler;
import club.boyuan.official.integration.agent.AgentAdminClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

/**
 * 新代理路由(周期复盘、组织记忆、知识库运营、校准案例)走一遍 DispatcherServlet
 * (standalone MockMvc,不起 Spring 上下文、
 * 不连数据库,Agent 客户端是替身)。直调控制器方法的单测看不到这些:
 * - 前端实际请求的 URL、HTTP 方法、查询参数名(snake_case)能不能路由到对应方法;
 * - 写到 HTTP 响应里的状态码与信封({code, message, data});
 * - Agent 不可达时 @ExceptionHandler 真的转成 502;
 * - 路径变量不是整数、必填参数缺失时,在进控制器前就被拒,没有请求转发给 Agent。
 *
 * 权限不在这里测(standalone 不装方法级鉴权),见 AgentProxySecurityTest。
 */
class AgentProxyRoutingTest {

    private static final String AUTH = "Bearer user-jwt";
    private static final String BASE = "/api/admin/agent";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAdminClient client =
            mock(AgentAdminClient.class, invocation -> ResponseEntity.ok("{}"));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AgentRetrospectiveProxyController(client, objectMapper),
                    new AgentOrgMemoryProxyController(client, objectMapper),
                    new AgentKbAdminController(client, objectMapper),
                    new AgentEvaluationProxyController(client, objectMapper))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", AUTH);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return authed(request).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ── 路由:URL / 方法 / 参数名 ──

    @Test
    @DisplayName("周期复盘 7 条路由:前端的 URL 与 snake_case 参数名都能路由到位")
    void retrospectiveRoutes() throws Exception {
        mvc.perform(json(post(BASE + "/retrospective/snapshot"), "{\"cycle_id\":2026}"))
                .andExpect(status().isOk());
        verify(client).postRetrospectiveSnapshot(AUTH, Map.of("cycle_id", 2026));

        mvc.perform(authed(get(BASE + "/retrospective/snapshot")
                        .param("cycle_id", "2026").param("version", "2").param("baseline_cycle_id", "2025")))
                .andExpect(status().isOk());
        verify(client).getRetrospectiveSnapshot(AUTH, 2026, 2, 2025);

        mvc.perform(authed(get(BASE + "/retrospective/snapshot").param("cycle_id", "2026")))
                .andExpect(status().isOk());
        verify(client).getRetrospectiveSnapshot(AUTH, 2026, null, null);

        mvc.perform(authed(get(BASE + "/retrospective/snapshots").param("cycle_id", "2026")))
                .andExpect(status().isOk());
        verify(client).getRetrospectiveSnapshots(AUTH, 2026);

        mvc.perform(json(post(BASE + "/retrospective/run"), "{\"cycle_id\":2026,\"baseline_cycle_id\":2025}"))
                .andExpect(status().isOk());
        verify(client).postRetrospectiveRun(AUTH, Map.of("cycle_id", 2026, "baseline_cycle_id", 2025));

        mvc.perform(authed(get(BASE + "/retrospective/report").param("cycle_id", "2026").param("version", "4")))
                .andExpect(status().isOk());
        verify(client).getRetrospectiveReport(AUTH, 2026, 4);

        mvc.perform(authed(get(BASE + "/retrospective/reports").param("cycle_id", "2026")))
                .andExpect(status().isOk());
        verify(client).getRetrospectiveReports(AUTH, 2026);

        mvc.perform(json(post(BASE + "/retrospective/publish"), "{\"cycle_id\":2026,\"version\":4}"))
                .andExpect(status().isOk());
        verify(client).postRetrospectivePublish(AUTH, Map.of("cycle_id", 2026, "version", 4));
    }

    @Test
    @DisplayName("知识库运营 8 条路由:/kb/ops/** 不被既有的 /kb/sources/** 截走")
    void kbOpsRoutes() throws Exception {
        mvc.perform(json(post(BASE + "/kb/ops/runs"), "{\"days\":14}")).andExpect(status().isOk());
        verify(client).postKbOpsRun(AUTH, Map.of("days", 14));

        mvc.perform(authed(get(BASE + "/kb/ops/runs").param("limit", "20"))).andExpect(status().isOk());
        verify(client).getKbOpsRuns(AUTH, 20);

        mvc.perform(authed(get(BASE + "/kb/ops/topics").param("run_id", "run-7f3a").param("scope", "all")))
                .andExpect(status().isOk());
        verify(client).getKbOpsTopics(AUTH, "run-7f3a", "all");

        mvc.perform(authed(get(BASE + "/kb/ops/topics"))).andExpect(status().isOk());
        verify(client).getKbOpsTopics(AUTH, null, null);

        mvc.perform(authed(get(BASE + "/kb/ops/drafts")
                        .param("status", "all").param("kind", "new_faq").param("page", "2").param("size", "50")))
                .andExpect(status().isOk());
        verify(client).getKbOpsDrafts(AUTH, "all", "new_faq", 2, 50);

        mvc.perform(authed(get(BASE + "/kb/ops/drafts/12"))).andExpect(status().isOk());
        verify(client).getKbOpsDraft(AUTH, 12L);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("question", "社费多少");
        edit.put("answer", "每学期 20 元");
        edit.put("cycle_id", null);
        mvc.perform(json(put(BASE + "/kb/ops/drafts/12"), objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());
        verify(client).putKbOpsDraft(AUTH, 12L, edit);

        mvc.perform(authed(post(BASE + "/kb/ops/drafts/12/approve"))).andExpect(status().isOk());
        verify(client).postKbOpsDraftApprove(AUTH, 12L);

        mvc.perform(authed(post(BASE + "/kb/ops/drafts/12/reject"))).andExpect(status().isOk());
        verify(client).postKbOpsDraftReject(AUTH, 12L);
    }

    @Test
    @DisplayName("忠实度抽样 3 条路由:/kb/ops/faithfulness/**,查询参数 run_id / problems_only")
    void kbFaithfulnessRoutes() throws Exception {
        mvc.perform(json(post(BASE + "/kb/ops/faithfulness/runs"), "{\"days\":14,\"sample_size\":50}"))
                .andExpect(status().isOk());
        verify(client).postKbFaithfulnessRun(AUTH, Map.of("days", 14, "sample_size", 50));

        mvc.perform(authed(post(BASE + "/kb/ops/faithfulness/runs"))).andExpect(status().isOk());
        verify(client).postKbFaithfulnessRun(AUTH, null);

        mvc.perform(authed(get(BASE + "/kb/ops/faithfulness/runs").param("limit", "20")))
                .andExpect(status().isOk());
        verify(client).getKbFaithfulnessRuns(AUTH, 20);

        mvc.perform(authed(get(BASE + "/kb/ops/faithfulness/samples")
                        .param("run_id", "kbfaith_1").param("problems_only", "true")))
                .andExpect(status().isOk());
        verify(client).getKbFaithfulnessSamples(AUTH, "kbfaith_1", true);

        mvc.perform(authed(get(BASE + "/kb/ops/faithfulness/samples"))).andExpect(status().isOk());
        verify(client).getKbFaithfulnessSamples(AUTH, null, null);
    }

    @Test
    @DisplayName("组织记忆 4 条路由:列表在 /org-memory 本身,勾选/退役/续期是它下面的 POST")
    void orgMemoryRoutes() throws Exception {
        mvc.perform(authed(get(BASE + "/org-memory").param("status", "approved"))).andExpect(status().isOk());
        verify(client).getOrgMemories(AUTH, "approved");

        mvc.perform(authed(get(BASE + "/org-memory"))).andExpect(status().isOk());
        verify(client).getOrgMemories(AUTH, null);

        mvc.perform(json(post(BASE + "/org-memory/approve"),
                        "{\"cycle_id\":2026,\"version\":4,\"lesson_index\":0,\"audience\":\"member\"}"))
                .andExpect(status().isOk());
        verify(client).postOrgMemoryApprove(AUTH,
                Map.of("cycle_id", 2026, "version", 4, "lesson_index", 0, "audience", "member"));

        mvc.perform(json(post(BASE + "/org-memory/retire"), "{\"memory_id\":\"org_9\"}"))
                .andExpect(status().isOk());
        verify(client).postOrgMemoryRetire(AUTH, Map.of("memory_id", "org_9"));

        mvc.perform(json(post(BASE + "/org-memory/renew"), "{\"memory_id\":\"org_9\",\"valid_days\":180}"))
                .andExpect(status().isOk());
        verify(client).postOrgMemoryRenew(AUTH, Map.of("memory_id", "org_9", "valid_days", 180));
    }

    @Test
    @DisplayName("启动分析:不带请求体也能进(转发 null),空对象照样转发")
    void kbOpsRunBodyIsOptional() throws Exception {
        mvc.perform(authed(post(BASE + "/kb/ops/runs"))).andExpect(status().isOk());
        verify(client).postKbOpsRun(AUTH, null);

        mvc.perform(json(post(BASE + "/kb/ops/runs"), "{}")).andExpect(status().isOk());
        verify(client).postKbOpsRun(AUTH, Map.of());
    }

    @Test
    @DisplayName("校准案例 4 条路由:查询参数用 snake_case(include_retired),停用走 DELETE")
    void calibrationRoutes() throws Exception {
        mvc.perform(authed(get(BASE + "/evaluation/calibration/cases")
                        .param("checklist", "技术部").param("item", "C++ 基础")
                        .param("include_retired", "true").param("limit", "50").param("offset", "100")))
                .andExpect(status().isOk());
        verify(client).getCalibrationCases(AUTH, "技术部", "C++ 基础", true, 50, 100);

        mvc.perform(authed(get(BASE + "/evaluation/calibration/cases"))).andExpect(status().isOk());
        verify(client).getCalibrationCases(AUTH, null, null, null, null, null);

        String seed = "{\"checklist\":\"技术部\",\"item\":\"有项目经历\",\"met\":true,"
                + "\"quote\":\"做过课程设计\",\"reason\":\"课程设计算项目经历\"}";
        mvc.perform(json(post(BASE + "/evaluation/calibration/cases"), seed)).andExpect(status().isOk());
        verify(client).postCalibrationCase(AUTH, Map.of(
                "checklist", "技术部", "item", "有项目经历", "met", true,
                "quote", "做过课程设计", "reason", "课程设计算项目经历"));

        mvc.perform(authed(delete(BASE + "/evaluation/calibration/cases/8"))).andExpect(status().isOk());
        verify(client).deleteCalibrationCase(AUTH, 8L);

        mvc.perform(json(post(BASE + "/evaluation/calibration/harvest"), "{\"cycle_id\":2025}"))
                .andExpect(status().isOk());
        verify(client).postCalibrationHarvest(AUTH, Map.of("cycle_id", 2025));
    }

    // ── 写到 HTTP 响应里的状态码与信封 ──

    @Test
    @DisplayName("上游 202:HTTP 仍是 202,信封 code=200、data 是上游响应体")
    void acceptedIsWrittenAs202WithEnvelope() throws Exception {
        when(client.postKbOpsRun(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"r1\",\"status\":\"running\"}"));
        when(client.postRetrospectiveRun(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"version\":4,\"status\":\"generating\"}"));

        mvc.perform(json(post(BASE + "/kb/ops/runs"), "{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.run_id").value("r1"))
                .andExpect(jsonPath("$.data.status").value("running"));

        mvc.perform(json(post(BASE + "/retrospective/run"), "{\"cycle_id\":2026}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.status").value("generating"));
    }

    @Test
    @DisplayName("追加路由的状态码:启动抽样 202、勾选 201 保持;未启用 409、过不了闸门 422 原样写出")
    void faithfulnessAndOrgMemoryStatusesAreWrittenThrough() throws Exception {
        when(client.postKbFaithfulnessRun(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.ACCEPTED, "{\"run_id\":\"kbfaith_1\",\"status\":\"running\"}"))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"忠实度抽样没有在配置里打开\"}"));
        when(client.postOrgMemoryApprove(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.CREATED, "{\"memory_id\":\"org_9\",\"status\":\"approved\"}"))
                .thenReturn(upstream(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"内容疑似含个人信息\"}"));
        when(client.postOrgMemoryRetire(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"这条组织记忆已经退役\"}"));

        mvc.perform(json(post(BASE + "/kb/ops/faithfulness/runs"), "{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.run_id").value("kbfaith_1"));
        mvc.perform(json(post(BASE + "/kb/ops/faithfulness/runs"), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("忠实度抽样没有在配置里打开"));

        String approve = "{\"cycle_id\":2026,\"version\":4,\"lesson_index\":0}";
        mvc.perform(json(post(BASE + "/org-memory/approve"), approve))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.memory_id").value("org_9"));
        mvc.perform(json(post(BASE + "/org-memory/approve"), approve))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value("内容疑似含个人信息"));

        mvc.perform(json(post(BASE + "/org-memory/retire"), "{\"memory_id\":\"org_old\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("这条组织记忆已经退役"));
    }

    @Test
    @DisplayName("上游 404/409/503:HTTP 状态码各自保持,信封 code 同状态码、message 是 detail")
    void errorStatusesAreWrittenThrough() throws Exception {
        when(client.getKbOpsDraft(AUTH, 404L))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"草稿不存在\"}"));
        when(client.postCalibrationHarvest(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"从往届评分卡收割校准案例的功能未启用\"}"));
        when(client.postRetrospectiveRun(eq(AUTH), any()))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE, "{\"detail\":\"复盘报告生成尚未开启\"}"));

        mvc.perform(authed(get(BASE + "/kb/ops/drafts/404")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("草稿不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(json(post(BASE + "/evaluation/calibration/harvest"), "{\"cycle_id\":2025}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("从往届评分卡收割校准案例的功能未启用"));

        mvc.perform(json(post(BASE + "/retrospective/run"), "{\"cycle_id\":2026}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("复盘报告生成尚未开启"));
    }

    @Test
    @DisplayName("Agent 不可达:各控制器的 @ExceptionHandler 都转成 HTTP 502 + code 502")
    void unreachableBecomes502() throws Exception {
        when(client.getRetrospectiveSnapshots(AUTH, 2026))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postKbOpsDraftApprove(AUTH, 12L))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.deleteCalibrationCase(AUTH, 8L))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.getOrgMemories(AUTH, null))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.getKbFaithfulnessSamples(AUTH, null, null))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(authed(get(BASE + "/retrospective/snapshots").param("cycle_id", "2026")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        mvc.perform(authed(post(BASE + "/kb/ops/drafts/12/approve")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        mvc.perform(authed(delete(BASE + "/evaluation/calibration/cases/8")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        mvc.perform(authed(get(BASE + "/org-memory")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        mvc.perform(authed(get(BASE + "/kb/ops/faithfulness/samples")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    // ── 进控制器前就被拒:不转发 ──

    @Test
    @DisplayName("草稿号/案例号不是整数:400,没有请求转发给 Agent")
    void nonIntegerPathVariablesAreRejectedBeforeForwarding() throws Exception {
        mvc.perform(authed(get(BASE + "/kb/ops/drafts/abc"))).andExpect(status().isBadRequest());
        mvc.perform(json(put(BASE + "/kb/ops/drafts/1e3"), "{\"question\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(post(BASE + "/kb/ops/drafts/12abc/approve"))).andExpect(status().isBadRequest());
        mvc.perform(authed(delete(BASE + "/evaluation/calibration/cases/8,9")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("想借路径变量拼出别的下游路径(../、编码斜杠):都被拒,没有请求转发给 Agent")
    void pathVariablesCannotReachOtherAgentPaths() throws Exception {
        // 状态码取决于全局兜底(本仓对「没有匹配的路由」返回 500),这里只钉住「没放行、没转发」
        mvc.perform(authed(post(BASE + "/kb/ops/drafts/{id}/reject", "12/../../config")))
                .andExpect(notSuccessful());
        mvc.perform(authed(get(BASE + "/kb/ops/drafts/{id}", "..%2F..%2Fconfig")))
                .andExpect(notSuccessful());
        mvc.perform(authed(delete(BASE + "/evaluation/calibration/cases/{id}", "../harvest")))
                .andExpect(notSuccessful());

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("必填的 cycle_id 缺失或不是整数:400,没有请求转发给 Agent")
    void missingOrMalformedCycleIdIsRejectedBeforeForwarding() throws Exception {
        mvc.perform(authed(get(BASE + "/retrospective/snapshot"))).andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/retrospective/snapshots").param("cycleId", "2026")))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/retrospective/report").param("cycle_id", "abc")))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/retrospective/reports"))).andExpect(status().isBadRequest());

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("没有声明的方法不可用:草稿不能 DELETE,判例不能 PUT,组织记忆不能 DELETE,都不转发")
    void undeclaredMethodsAreNotRouted() throws Exception {
        mvc.perform(authed(delete(BASE + "/kb/ops/drafts/12"))).andExpect(notSuccessful());
        mvc.perform(json(put(BASE + "/evaluation/calibration/cases/8"), "{}")).andExpect(notSuccessful());
        mvc.perform(authed(delete(BASE + "/org-memory/org_9"))).andExpect(notSuccessful());
        mvc.perform(authed(get(BASE + "/org-memory/approve"))).andExpect(notSuccessful());

        verifyNoInteractions(client);
    }

    private static ResultMatcher notSuccessful() {
        return result -> assertTrue(result.getResponse().getStatus() >= 400,
                "应被拒绝,实际状态码 " + result.getResponse().getStatus());
    }
}
