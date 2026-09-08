package club.boyuan.official.domain.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
}
