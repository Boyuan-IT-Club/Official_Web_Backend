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

/** B7:评测代理契约单测(同 AgentKbAdminControllerTest 先例)。 */
class AgentEvaluationProxyControllerTest {

    private static final String AUTH = "Bearer reviewer-jwt";

    private final AgentAdminClient client = mock(AgentAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentEvaluationProxyController controller =
            new AgentEvaluationProxyController(client, objectMapper);

    private static ResponseEntity<String> upstream(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    @Test
    @DisplayName("队列:cycleId/queue 原样转发,2xx 包信封")
    void queue_forwardsAndWraps() throws Exception {
        when(client.getEvaluationQueue(AUTH, 2026, "zero"))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"queue\":\"zero\"}"));
        var resp = controller.queue(AUTH, 2026, "zero");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"items\":[],\"queue\":\"zero\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("采纳:请求体(含分数)原样转发")
    void adopt_forwardsBody() throws Exception {
        Map<String, Object> body = Map.of("resume_id", 9, "cycle_id", 2026, "score", 66);
        when(client.postEvaluationAdopt(AUTH, body))
                .thenReturn(upstream(HttpStatus.OK, "{\"status\":\"adopted\"}"));
        var resp = controller.adopt(AUTH, body);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"status\":\"adopted\"}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("评分卡/题库:resumeId+cycleId 转发;404 人话透传")
    void scorecardAndQbank_forward() {
        when(client.getEvaluationScorecard(AUTH, 9L, 2026))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"该候选暂无评分卡\"}"));
        when(client.getEvaluationQbank(AUTH, 9L, 2026))
                .thenReturn(upstream(HttpStatus.OK, "{\"envelope\":{}}"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.scorecard(AUTH, 9L, 2026).getStatusCode());
        assertEquals("该候选暂无评分卡",
                controller.scorecard(AUTH, 9L, 2026).getBody().getMessage());
        assertEquals(HttpStatus.OK, controller.qbank(AUTH, 9L, 2026).getStatusCode());
    }

    @Test
    @DisplayName("可选参数缺省时转发 null")
    void queue_forwardsNulls() {
        when(client.getEvaluationQueue(eq(AUTH), eq(2026), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));
        assertEquals(HttpStatus.OK, controller.queue(AUTH, 2026, null).getStatusCode());
    }

    // ── 校准案例(/calibration/**)──

    @Test
    @DisplayName("案例列表:过滤与分页参数原样转发,缺省转发 null")
    void calibrationCases_forwardParams() throws Exception {
        when(client.getCalibrationCases(AUTH, "技术部", "C++ 基础", true, 50, 100))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0,\"watermark\":7,\"enabled\":false}"));
        when(client.getCalibrationCases(eq(AUTH), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(upstream(HttpStatus.OK, "{}"));

        var resp = controller.calibrationCases(AUTH, "技术部", "C++ 基础", true, 50, 100);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"items\":[],\"total\":0,\"watermark\":7,\"enabled\":false}"),
                resp.getBody().getData());
        assertEquals(HttpStatus.OK,
                controller.calibrationCases(AUTH, null, null, null, null, null).getStatusCode());
    }

    @Test
    @DisplayName("录入判例:请求体原样转发,201 保持;入库筛查不过 400、嵌入服务不可用 503")
    void addCalibrationCase_forwardsBodyAndKeepsStatuses() throws Exception {
        Map<String, Object> body = Map.of(
                "checklist", "技术部", "item", "有项目经历", "met", true,
                "quote", "做过课程设计", "reason", "课程设计算项目经历");
        Map<String, Object> rejected = Map.of("checklist", "技术部", "item", "不存在的判定项");
        Map<String, Object> embeddingDown = Map.of("checklist", "技术部", "item", "有项目经历", "met", false);
        when(client.postCalibrationCase(AUTH, body))
                .thenReturn(upstream(HttpStatus.CREATED, "{\"case_id\":8}"));
        when(client.postCalibrationCase(AUTH, rejected))
                .thenReturn(upstream(HttpStatus.BAD_REQUEST, "{\"detail\":\"判定项不存在\"}"));
        when(client.postCalibrationCase(AUTH, embeddingDown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE,
                        "{\"detail\":\"嵌入服务不可用,判例未入库,请稍后重试\"}"));

        var created = controller.addCalibrationCase(AUTH, body);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(objectMapper.readTree("{\"case_id\":8}"), created.getBody().getData());

        var bad = controller.addCalibrationCase(AUTH, rejected);
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
        assertEquals("判定项不存在", bad.getBody().getMessage());

        var unavailable = controller.addCalibrationCase(AUTH, embeddingDown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("嵌入服务不可用,判例未入库,请稍后重试", unavailable.getBody().getMessage());
    }

    @Test
    @DisplayName("停用判例:案例号转发;不存在或已停用 404")
    void retireCalibrationCase_forwardsIdAndKeeps404() throws Exception {
        when(client.deleteCalibrationCase(AUTH, 8L))
                .thenReturn(upstream(HttpStatus.OK, "{\"case_id\":8,\"status\":\"retired\"}"));
        when(client.deleteCalibrationCase(AUTH, 9L))
                .thenReturn(upstream(HttpStatus.NOT_FOUND, "{\"detail\":\"判例不存在或已停用\"}"));

        var ok = controller.retireCalibrationCase(AUTH, 8L);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"case_id\":8,\"status\":\"retired\"}"),
                ok.getBody().getData());

        var missing = controller.retireCalibrationCase(AUTH, 9L);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("判例不存在或已停用", missing.getBody().getMessage());
    }

    @Test
    @DisplayName("收割:请求体转发;功能未启用 409、确认不了周期是否结束 503 各自保持")
    void harvestCalibrationCases_forwardsBodyAndKeepsStatuses() throws Exception {
        Map<String, Object> ended = Map.of("cycle_id", 2025);
        Map<String, Object> disabled = Map.of("cycle_id", 2024);
        Map<String, Object> unknown = Map.of("cycle_id", 2023);
        when(client.postCalibrationHarvest(AUTH, ended))
                .thenReturn(upstream(HttpStatus.OK, "{\"cycle_id\":2025,\"added\":12}"));
        when(client.postCalibrationHarvest(AUTH, disabled))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"从往届评分卡收割校准案例的功能未启用\"}"));
        when(client.postCalibrationHarvest(AUTH, unknown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE, "{\"detail\":\"无法确认该周期是否已结束,未执行\"}"));

        var ok = controller.harvestCalibrationCases(AUTH, ended);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(objectMapper.readTree("{\"cycle_id\":2025,\"added\":12}"), ok.getBody().getData());

        var off = controller.harvestCalibrationCases(AUTH, disabled);
        assertEquals(HttpStatus.CONFLICT, off.getStatusCode());
        assertEquals(409, off.getBody().getCode());
        assertEquals("从往届评分卡收割校准案例的功能未启用", off.getBody().getMessage());

        var unavailable = controller.harvestCalibrationCases(AUTH, unknown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("无法确认该周期是否已结束,未执行", unavailable.getBody().getMessage());
    }

    @Test
    @DisplayName("校准案例:Agent 不可达时读、写接口都上抛,@ExceptionHandler 转 502")
    void calibrationUnreachableBecomes502() {
        Map<String, Object> body = Map.of("cycle_id", 2025);
        when(client.getCalibrationCases(AUTH, null, null, null, null, null))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postCalibrationHarvest(AUTH, body))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class,
                () -> controller.calibrationCases(AUTH, null, null, null, null, null));
        assertThrows(ResourceAccessException.class,
                () -> controller.harvestCalibrationCases(AUTH, body));

        var handled = controller.handleUnreachable(new ResourceAccessException("Connection refused"));
        assertEquals(HttpStatus.BAD_GATEWAY, handled.getStatusCode());
        assertEquals(502, handled.getBody().getCode());
    }

    // ── 评分条款候选(/clauses/**)──

    @Test
    @DisplayName("条款候选列表:cycle_id 转发,2xx 包信封")
    void clauseCandidates_forwardsAndWraps() throws Exception {
        when(client.getEvaluationClauses(AUTH, 2025))
                .thenReturn(upstream(HttpStatus.OK, "{\"items\":[],\"total\":0,\"cycle_id\":2025,\"enabled\":false}"));

        var resp = controller.clauseCandidates(AUTH, 2025);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"items\":[],\"total\":0,\"cycle_id\":2025,\"enabled\":false}"),
                resp.getBody().getData());
    }

    @Test
    @DisplayName("生成条款候选:请求体转发;未启用/周期还开着 409、确认不了周期 503、模型输出不合规 502 各自保持")
    void generateClauseCandidates_forwardsBodyAndKeepsStatuses() throws Exception {
        Map<String, Object> ended = Map.of("cycle_id", 2025);
        Map<String, Object> disabled = Map.of("cycle_id", 2024);
        Map<String, Object> open = Map.of("cycle_id", 2026);
        Map<String, Object> unknown = Map.of("cycle_id", 2023);
        Map<String, Object> badOutput = Map.of("cycle_id", 2022);
        when(client.postEvaluationClausesGenerate(AUTH, ended))
                .thenReturn(upstream(HttpStatus.OK, "{\"cycle_id\":2025,\"candidates_stored\":4}"));
        when(client.postEvaluationClausesGenerate(AUTH, disabled))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"生成评分条款候选的功能未启用\"}"));
        when(client.postEvaluationClausesGenerate(AUTH, open))
                .thenReturn(upstream(HttpStatus.CONFLICT, "{\"detail\":\"该周期还在进行中,结束后才能生成条款候选\"}"));
        when(client.postEvaluationClausesGenerate(AUTH, unknown))
                .thenReturn(upstream(HttpStatus.SERVICE_UNAVAILABLE, "{\"detail\":\"无法确认该周期是否已结束,未执行\"}"));
        when(client.postEvaluationClausesGenerate(AUTH, badOutput))
                .thenReturn(upstream(HttpStatus.BAD_GATEWAY,
                        "{\"detail\":\"模型没有给出合规的候选,本次未写入;原有的候选保留\"}"));

        var ok = controller.generateClauseCandidates(AUTH, ended);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(
                objectMapper.readTree("{\"cycle_id\":2025,\"candidates_stored\":4}"),
                ok.getBody().getData());

        var off = controller.generateClauseCandidates(AUTH, disabled);
        assertEquals(HttpStatus.CONFLICT, off.getStatusCode());
        assertEquals(409, off.getBody().getCode());
        assertEquals("生成评分条款候选的功能未启用", off.getBody().getMessage());

        var stillOpen = controller.generateClauseCandidates(AUTH, open);
        assertEquals(HttpStatus.CONFLICT, stillOpen.getStatusCode());
        assertEquals("该周期还在进行中,结束后才能生成条款候选", stillOpen.getBody().getMessage());

        var unavailable = controller.generateClauseCandidates(AUTH, unknown);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("无法确认该周期是否已结束,未执行", unavailable.getBody().getMessage());

        var modelFailed = controller.generateClauseCandidates(AUTH, badOutput);
        assertEquals(HttpStatus.BAD_GATEWAY, modelFailed.getStatusCode());
        assertEquals(502, modelFailed.getBody().getCode());
        assertEquals("模型没有给出合规的候选,本次未写入;原有的候选保留", modelFailed.getBody().getMessage());
    }

    @Test
    @DisplayName("评分条款候选:Agent 不可达时读、写接口都上抛 ResourceAccessException")
    void clausesUnreachableExceptionPropagates() {
        Map<String, Object> body = Map.of("cycle_id", 2025);
        when(client.getEvaluationClauses(AUTH, 2025))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(client.postEvaluationClausesGenerate(AUTH, body))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> controller.clauseCandidates(AUTH, 2025));
        assertThrows(ResourceAccessException.class, () -> controller.generateClauseCandidates(AUTH, body));
    }
}
