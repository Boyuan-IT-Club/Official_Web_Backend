package club.boyuan.official.integration.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

/**
 * AgentAdminClient 出站契约:对着本机回环上的一个桩 HTTP 服务(JDK 自带
 * HttpServer,随机端口,不连任何真实服务),核对**真正发到下游的**方法、路径、
 * 查询串、Authorization 头与请求体,以及上游状态码与响应体原样取回。
 *
 * 控制器单测里客户端是替身,看不到下游地址怎么拼;路径拼错、参数名写错、
 * 查询值没转义这类问题只有在这里才看得见。覆盖周期复盘、组织记忆、知识库运营
 * (含忠实度抽样)、校准案例的方法。
 */
class AgentAdminClientTest {

    private static final String AUTH = "Bearer user-jwt";

    /** 桩收到的一次请求。 */
    private record Received(String method, String rawPath, String rawQuery,
                            String authorization, String contentType, String body) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopyOnWriteArrayList<Received> received = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private AgentAdminClient client;

    /** 桩的下一次应答;用例各自设置。 */
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{}";
    private volatile long replyDelayMillis = 0;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            if (replyDelayMillis > 0) {
                try {
                    Thread.sleep(replyDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(replyStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new AgentAdminClient(baseUrl());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/agent";
    }

    private void reply(int status, String body) {
        replyStatus = status;
        replyBody = body;
    }

    /** 调一次客户端,断言下游恰好收到一次请求并返回它。 */
    private Received call(Supplier<ResponseEntity<String>> invocation) {
        received.clear();
        invocation.get();
        assertEquals(1, received.size(), "下游应恰好收到一次请求");
        Received request = received.get(0);
        assertEquals(AUTH, request.authorization(), "Authorization 头应原样带到下游");
        return request;
    }

    private void assertRequest(Received request, String method, String path, String query) {
        assertEquals(method, request.method());
        assertEquals("/api/agent" + path, request.rawPath());
        assertEquals(query, request.rawQuery());
    }

    private void assertJsonBody(Received request, Map<String, Object> expected) throws IOException {
        assertTrue(request.contentType() != null && request.contentType().startsWith("application/json"),
                "请求体应按 JSON 发出,实际 Content-Type=" + request.contentType());
        assertEquals(objectMapper.valueToTree(expected), objectMapper.readTree(request.body()));
    }

    private void assertNoBody(Received request) {
        assertEquals("", request.body());
    }

    // ── 周期复盘 ──

    @Test
    @DisplayName("复盘快照:采集 POST 带体;读取与列表的查询参数用 Agent 的 snake_case 名")
    void retrospectiveSnapshotRoutes() throws IOException {
        Map<String, Object> body = Map.of("cycle_id", 2026);

        Received take = call(() -> client.postRetrospectiveSnapshot(AUTH, body));
        assertRequest(take, "POST", "/admin/retrospective/snapshot", null);
        assertJsonBody(take, body);

        Received full = call(() -> client.getRetrospectiveSnapshot(AUTH, 2026, 2, 2025));
        assertRequest(full, "GET", "/admin/retrospective/snapshot",
                "cycle_id=2026&version=2&baseline_cycle_id=2025");

        Received latest = call(() -> client.getRetrospectiveSnapshot(AUTH, 2026, null, null));
        assertRequest(latest, "GET", "/admin/retrospective/snapshot", "cycle_id=2026");

        Received list = call(() -> client.getRetrospectiveSnapshots(AUTH, 2026));
        assertRequest(list, "GET", "/admin/retrospective/snapshots", "cycle_id=2026");
    }

    @Test
    @DisplayName("复盘报告:生成/发布 POST 带体;读取与列表带 cycle_id(可选 version)")
    void retrospectiveReportRoutes() throws IOException {
        Map<String, Object> runBody = Map.of("cycle_id", 2026, "baseline_cycle_id", 2025);
        Map<String, Object> publishBody = Map.of("cycle_id", 2026, "version", 4);

        Received run = call(() -> client.postRetrospectiveRun(AUTH, runBody));
        assertRequest(run, "POST", "/admin/retrospective/run", null);
        assertJsonBody(run, runBody);

        Received report = call(() -> client.getRetrospectiveReport(AUTH, 2026, 4));
        assertRequest(report, "GET", "/admin/retrospective/report", "cycle_id=2026&version=4");

        Received latest = call(() -> client.getRetrospectiveReport(AUTH, 2026, null));
        assertRequest(latest, "GET", "/admin/retrospective/report", "cycle_id=2026");

        Received reports = call(() -> client.getRetrospectiveReports(AUTH, 2026));
        assertRequest(reports, "GET", "/admin/retrospective/reports", "cycle_id=2026");

        Received publish = call(() -> client.postRetrospectivePublish(AUTH, publishBody));
        assertRequest(publish, "POST", "/admin/retrospective/publish", null);
        assertJsonBody(publish, publishBody);
    }

    // ── 知识库运营 ──

    @Test
    @DisplayName("知识库运营·分析与主题:启动可带体可不带;limit/run_id/scope 只在给了时才拼")
    void kbOpsRunAndTopicRoutes() throws IOException {
        Map<String, Object> body = Map.of("days", 14);

        Received start = call(() -> client.postKbOpsRun(AUTH, body));
        assertRequest(start, "POST", "/admin/kb/ops/runs", null);
        assertJsonBody(start, body);

        Received startDefault = call(() -> client.postKbOpsRun(AUTH, null));
        assertRequest(startDefault, "POST", "/admin/kb/ops/runs", null);
        assertNoBody(startDefault);

        Received runs = call(() -> client.getKbOpsRuns(AUTH, 20));
        assertRequest(runs, "GET", "/admin/kb/ops/runs", "limit=20");

        Received runsDefault = call(() -> client.getKbOpsRuns(AUTH, null));
        assertRequest(runsDefault, "GET", "/admin/kb/ops/runs", null);

        Received topics = call(() -> client.getKbOpsTopics(AUTH, "run-7f3a", "untriaged"));
        assertRequest(topics, "GET", "/admin/kb/ops/topics", "run_id=run-7f3a&scope=untriaged");

        Received topicsDefault = call(() -> client.getKbOpsTopics(AUTH, null, " "));
        assertRequest(topicsDefault, "GET", "/admin/kb/ops/topics", null);
    }

    @Test
    @DisplayName("知识库运营·草稿:列表参数、草稿号进路径、修改带体、批准/驳回不带体")
    void kbOpsDraftRoutes() throws IOException {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("question", "社费多少");
        edit.put("answer", "每学期 20 元");
        edit.put("cycle_id", null);

        Received list = call(() -> client.getKbOpsDrafts(AUTH, "all", "new_faq", 2, 50));
        assertRequest(list, "GET", "/admin/kb/ops/drafts", "status=all&kind=new_faq&page=2&size=50");

        Received listDefault = call(() -> client.getKbOpsDrafts(AUTH, null, null, null, null));
        assertRequest(listDefault, "GET", "/admin/kb/ops/drafts", null);

        Received detail = call(() -> client.getKbOpsDraft(AUTH, 12L));
        assertRequest(detail, "GET", "/admin/kb/ops/drafts/12", null);

        Received put = call(() -> client.putKbOpsDraft(AUTH, 12L, edit));
        assertRequest(put, "PUT", "/admin/kb/ops/drafts/12", null);
        assertJsonBody(put, edit);

        Received approve = call(() -> client.postKbOpsDraftApprove(AUTH, 12L));
        assertRequest(approve, "POST", "/admin/kb/ops/drafts/12/approve", null);
        assertNoBody(approve);

        Received reject = call(() -> client.postKbOpsDraftReject(AUTH, 12L));
        assertRequest(reject, "POST", "/admin/kb/ops/drafts/12/reject", null);
        assertNoBody(reject);
    }

    @Test
    @DisplayName("忠实度抽样:启动可带体可不带;limit/run_id/problems_only 只在给了时才拼")
    void kbFaithfulnessRoutes() throws IOException {
        Map<String, Object> body = Map.of("days", 14, "sample_size", 50);

        Received start = call(() -> client.postKbFaithfulnessRun(AUTH, body));
        assertRequest(start, "POST", "/admin/kb/ops/faithfulness/runs", null);
        assertJsonBody(start, body);

        Received startDefault = call(() -> client.postKbFaithfulnessRun(AUTH, null));
        assertRequest(startDefault, "POST", "/admin/kb/ops/faithfulness/runs", null);
        assertNoBody(startDefault);

        Received runs = call(() -> client.getKbFaithfulnessRuns(AUTH, 20));
        assertRequest(runs, "GET", "/admin/kb/ops/faithfulness/runs", "limit=20");

        Received runsDefault = call(() -> client.getKbFaithfulnessRuns(AUTH, null));
        assertRequest(runsDefault, "GET", "/admin/kb/ops/faithfulness/runs", null);

        Received samples = call(() -> client.getKbFaithfulnessSamples(AUTH, "kbfaith_1", true));
        assertRequest(samples, "GET", "/admin/kb/ops/faithfulness/samples", "run_id=kbfaith_1&problems_only=true");

        Received all = call(() -> client.getKbFaithfulnessSamples(AUTH, null, false));
        assertRequest(all, "GET", "/admin/kb/ops/faithfulness/samples", "problems_only=false");

        Received samplesDefault = call(() -> client.getKbFaithfulnessSamples(AUTH, null, null));
        assertRequest(samplesDefault, "GET", "/admin/kb/ops/faithfulness/samples", null);
    }

    // ── 组织记忆 ──

    @Test
    @DisplayName("组织记忆:列表在 /admin/org-memory 本身(可带 status);勾选/退役/续期 POST 带体")
    void orgMemoryRoutes() throws IOException {
        Map<String, Object> approve = Map.of(
                "cycle_id", 2026, "version", 4, "lesson_index", 0, "audience", "member");
        Map<String, Object> retire = Map.of("memory_id", "org_9");
        Map<String, Object> renew = Map.of("memory_id", "org_9", "valid_days", 180);

        Received list = call(() -> client.getOrgMemories(AUTH, "approved"));
        assertRequest(list, "GET", "/admin/org-memory", "status=approved");

        Received listAll = call(() -> client.getOrgMemories(AUTH, null));
        assertRequest(listAll, "GET", "/admin/org-memory", null);

        Received approved = call(() -> client.postOrgMemoryApprove(AUTH, approve));
        assertRequest(approved, "POST", "/admin/org-memory/approve", null);
        assertJsonBody(approved, approve);

        Received retired = call(() -> client.postOrgMemoryRetire(AUTH, retire));
        assertRequest(retired, "POST", "/admin/org-memory/retire", null);
        assertJsonBody(retired, retire);

        Received renewed = call(() -> client.postOrgMemoryRenew(AUTH, renew));
        assertRequest(renewed, "POST", "/admin/org-memory/renew", null);
        assertJsonBody(renewed, renew);
    }

    // ── 校准案例 ──

    @Test
    @DisplayName("校准案例:列表参数、录入/收割带体、停用走 DELETE 且案例号进路径")
    void calibrationRoutes() throws IOException {
        Map<String, Object> seed = Map.of(
                "checklist", "技术部", "item", "有项目经历", "met", true,
                "quote", "做过课程设计", "reason", "课程设计算项目经历");
        Map<String, Object> harvest = Map.of("cycle_id", 2025);

        Received list = call(() -> client.getCalibrationCases(AUTH, "tech", "projects", true, 50, 100));
        assertRequest(list, "GET", "/admin/evaluation/calibration/cases",
                "checklist=tech&item=projects&include_retired=true&limit=50&offset=100");

        Received listDefault = call(() -> client.getCalibrationCases(AUTH, null, null, null, null, null));
        assertRequest(listDefault, "GET", "/admin/evaluation/calibration/cases", null);

        Received add = call(() -> client.postCalibrationCase(AUTH, seed));
        assertRequest(add, "POST", "/admin/evaluation/calibration/cases", null);
        assertJsonBody(add, seed);

        Received retire = call(() -> client.deleteCalibrationCase(AUTH, 8L));
        assertRequest(retire, "DELETE", "/admin/evaluation/calibration/cases/8", null);

        Received reap = call(() -> client.postCalibrationHarvest(AUTH, harvest));
        assertRequest(reap, "POST", "/admin/evaluation/calibration/harvest", null);
        assertJsonBody(reap, harvest);
    }

    // ── 查询值转义 ──

    @Test
    @DisplayName("查询值按「值」严格转义:+ & = 空格 花括号 中文都不会破坏查询串,也不会被当成模板变量")
    void queryValuesAreStrictlyEncoded() {
        Received filtered = call(() -> client.getCalibrationCases(
                AUTH, "技术部", "C++ 基础&进阶={x}", null, null, null));

        assertEquals("/api/agent/admin/evaluation/calibration/cases", filtered.rawPath());
        assertEquals(
                "checklist=%E6%8A%80%E6%9C%AF%E9%83%A8"
                        + "&item=C%2B%2B%20%E5%9F%BA%E7%A1%80%26%E8%BF%9B%E9%98%B6%3D%7Bx%7D",
                filtered.rawQuery());
    }

    @Test
    @DisplayName("查询值里塞路径或额外参数:仍只是那个参数的值,到不了别的下游路径")
    void queryValuesCannotSmuggleAnotherPathOrParam() {
        Received topics = call(() -> client.getKbOpsTopics(AUTH, "../../config?x=1&scope=all#frag", null));

        assertEquals("/api/agent/admin/kb/ops/topics", topics.rawPath());
        assertEquals("run_id=..%2F..%2Fconfig%3Fx%3D1%26scope%3Dall%23frag", topics.rawQuery());
    }

    // ── 状态码与响应体原样取回 ──

    @Test
    @DisplayName("上游 202/201:状态码与响应体原样取回(不抹成 200)")
    void successStatusesArePreserved() {
        reply(202, "{\"run_id\":\"r1\",\"status\":\"running\"}");
        ResponseEntity<String> accepted = client.postKbOpsRun(AUTH, Map.of());
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        assertEquals("{\"run_id\":\"r1\",\"status\":\"running\"}", accepted.getBody());

        reply(202, "{\"version\":4,\"status\":\"generating\"}");
        assertEquals(HttpStatus.ACCEPTED,
                client.postRetrospectiveRun(AUTH, Map.of("cycle_id", 2026)).getStatusCode());

        reply(201, "{\"case_id\":8}");
        assertEquals(HttpStatus.CREATED,
                client.postCalibrationCase(AUTH, Map.of("checklist", "技术部")).getStatusCode());

        reply(202, "{\"run_id\":\"kbfaith_1\",\"status\":\"running\"}");
        assertEquals(HttpStatus.ACCEPTED, client.postKbFaithfulnessRun(AUTH, null).getStatusCode());

        reply(201, "{\"memory_id\":\"org_9\",\"status\":\"approved\"}");
        ResponseEntity<String> created = client.postOrgMemoryApprove(AUTH, Map.of("cycle_id", 2026));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("{\"memory_id\":\"org_9\",\"status\":\"approved\"}", created.getBody());
    }

    @Test
    @DisplayName("上游 4xx/5xx:不抛异常,状态码与 detail 响应体原样取回(404/409/503 各自区分)")
    void errorStatusesAreReturnedNotThrown() {
        reply(404, "{\"detail\":\"草稿不存在\"}");
        ResponseEntity<String> notFound = client.getKbOpsDraft(AUTH, 404L);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("{\"detail\":\"草稿不存在\"}", notFound.getBody());

        reply(409, "{\"detail\":\"已有一次分析在运行\"}");
        ResponseEntity<String> conflict = client.postKbOpsRun(AUTH, null);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("{\"detail\":\"已有一次分析在运行\"}", conflict.getBody());

        reply(503, "{\"detail\":\"复盘报告生成尚未开启\"}");
        ResponseEntity<String> unavailable = client.postRetrospectiveRun(AUTH, Map.of("cycle_id", 2026));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("{\"detail\":\"复盘报告生成尚未开启\"}", unavailable.getBody());

        reply(403, "{\"detail\":\"需要 evaluation:run 权限\"}");
        assertEquals(HttpStatus.FORBIDDEN,
                client.deleteCalibrationCase(AUTH, 8L).getStatusCode());

        reply(500, "{\"detail\":\"收割失败,请稍后重试\"}");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                client.postCalibrationHarvest(AUTH, Map.of("cycle_id", 2025)).getStatusCode());

        reply(409, "{\"detail\":\"忠实度抽样没有在配置里打开\"}");
        ResponseEntity<String> disabled = client.postKbFaithfulnessRun(AUTH, Map.of());
        assertEquals(HttpStatus.CONFLICT, disabled.getStatusCode());
        assertEquals("{\"detail\":\"忠实度抽样没有在配置里打开\"}", disabled.getBody());

        reply(422, "{\"detail\":\"内容疑似含个人信息\"}");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                client.postOrgMemoryApprove(AUTH, Map.of("cycle_id", 2026)).getStatusCode());

        reply(409, "{\"detail\":\"这条组织记忆已经退役\"}");
        assertEquals(HttpStatus.CONFLICT,
                client.postOrgMemoryRenew(AUTH, Map.of("memory_id", "org_old")).getStatusCode());
    }

    // ── 不可达与超时 ──

    @Test
    @DisplayName("Agent 不可达(连接被拒):读、写都抛 ResourceAccessException(控制器转 502)")
    void unreachableAgentThrowsResourceAccessException() {
        String deadBaseUrl = baseUrl();
        server.stop(0); // 端口随即关闭,之后的连接被拒
        AgentAdminClient deadClient = new AgentAdminClient(deadBaseUrl);

        assertThrows(ResourceAccessException.class,
                () -> deadClient.getRetrospectiveSnapshots(AUTH, 2026));
        assertThrows(ResourceAccessException.class,
                () -> deadClient.postKbOpsDraftApprove(AUTH, 12L));
        assertThrows(ResourceAccessException.class,
                () -> deadClient.postCalibrationHarvest(AUTH, Map.of("cycle_id", 2025)));
    }

    @Test
    @DisplayName("读超时分两档:同步做重活的接口(采集快照/生成报告/收割)走长超时,其余走默认")
    void slowRoutesUseTheLongReadTimeout() {
        AgentAdminClient tiered = new AgentAdminClient(
                baseUrl(), Duration.ofMillis(200), Duration.ofSeconds(10));
        replyDelayMillis = 800; // 比默认档长、比长档短
        Map<String, Object> body = Map.of("cycle_id", 2026);

        // 长档:等得到应答
        assertEquals(HttpStatus.OK, tiered.postRetrospectiveSnapshot(AUTH, body).getStatusCode());
        assertEquals(HttpStatus.OK, tiered.postRetrospectiveRun(AUTH, body).getStatusCode());
        assertEquals(HttpStatus.OK, tiered.postCalibrationHarvest(AUTH, body).getStatusCode());

        // 默认档:超时按不可达处理
        assertThrows(ResourceAccessException.class,
                () -> tiered.getRetrospectiveSnapshots(AUTH, 2026));
        assertThrows(ResourceAccessException.class,
                () -> tiered.postRetrospectivePublish(AUTH, body));
    }

    @Test
    @DisplayName("桩默认应答 200 {}:响应体为空对象时原样取回,不是 null")
    void emptyObjectBodyIsReturnedAsIs() {
        ResponseEntity<String> resp = client.getKbOpsRuns(AUTH, null);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("{}", resp.getBody());
        assertNull(received.get(received.size() - 1).rawQuery());
    }
}
