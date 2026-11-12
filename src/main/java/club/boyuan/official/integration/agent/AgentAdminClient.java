package club.boyuan.official.integration.agent;

import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * 客服 Agent 管理端出站客户端(M6 #115)。
 *
 * 职责只有转发:Backend 不做任何管理逻辑(#103 决策),把官网用户的
 * Authorization 头原样带给 Agent——Agent 侧自行走 /auth/me 身份解析并
 * 校验 agent:monitor(与 Backend 的 @PreAuthorize 形成双道闸)。
 *
 * exchange() 原样取回上游状态码与响应体(401/403/404/400 透传,不包装),
 * 连接失败抛 ResourceAccessException 由控制器转 502。
 */
@Component
public class AgentAdminClient {

    /** 默认读超时:查询与单条写入。 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 长读超时:Agent 在响应前同步做完重活的接口(复盘指标采集要读后端统计与几张日志表,
     * 校准案例收割要逐条算向量)。与管理端前端给采集接口单独放宽的 60 秒对齐——
     * 按默认 15 秒掐断的话,Agent 其实采完了,页面却只看到 502。
     */
    private static final Duration SLOW_READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final RestClient slowRestClient;

    @Autowired
    public AgentAdminClient(@Value("${agent.base-url:http://127.0.0.1:8001/api/agent}") String baseUrl) {
        this(baseUrl, READ_TIMEOUT, SLOW_READ_TIMEOUT);
    }

    /** 可指定两档读超时(单测用短超时验证分档,不必真等 15 秒)。 */
    AgentAdminClient(String baseUrl, Duration readTimeout, Duration slowReadTimeout) {
        // 钉 HTTP/1.1:默认 HTTP_2 会对明文端点发 h2c 升级,uvicorn(h11) 对
        // 带 body 的升级请求直接判 Invalid HTTP request(night-run E2E 实测),
        // GET 能容忍、POST 不能。Agent 侧是 HTTP/1.1 服务,无需协商。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.restClient = buildRestClient(baseUrl, httpClient, readTimeout);
        this.slowRestClient = buildRestClient(baseUrl, httpClient, slowReadTimeout);
    }

    private static RestClient buildRestClient(String baseUrl, HttpClient httpClient, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /** 运营列表(user_id/thread_id/limit/offset 全部透传给 Agent)。 */
    public ResponseEntity<String> getConversations(
            String authorization, Integer userId, String threadId, Integer limit, Integer offset) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/conversations");
                    if (userId != null) {
                        uri.queryParam("user_id", userId);
                    }
                    if (threadId != null && !threadId.isBlank()) {
                        uri.queryParam("thread_id", threadId);
                    }
                    if (limit != null) {
                        uri.queryParam("limit", limit);
                    }
                    if (offset != null) {
                        uri.queryParam("offset", offset);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 单轮详情。 */
    public ResponseEntity<String> getConversation(String authorization, long conversationId) {
        return exchange(restClient.get()
                .uri("/admin/conversations/{id}", conversationId)
                .header("Authorization", authorization));
    }

    /** 配置回显(低敏实值 + 高敏掩码)。 */
    public ResponseEntity<String> getConfig(String authorization) {
        return exchange(restClient.get().uri("/admin/config").header("Authorization", authorization));
    }

    /** 改低敏配置(Agent 侧热生效)。 */
    public ResponseEntity<String> putConfig(String authorization, Map<String, String> body) {
        return exchange(restClient.put().uri("/admin/config")
                .header("Authorization", authorization)
                .body(body));
    }


    /** 会话列表(按用户过滤可选)。 */
    public ResponseEntity<String> getSessions(String authorization, Integer userId) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/sessions");
                    if (userId != null) {
                        uri.queryParam("user_id", userId);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 会话原文回看。 */
    public ResponseEntity<String> getSessionMessages(String authorization, String threadId) {
        return exchange(restClient.get()
                .uri("/admin/sessions/{threadId}/messages", threadId)
                .header("Authorization", authorization));
    }


    // ── 知识库代理(RAG #134 R5):/admin/kb* —— 双道闸 kb:manage
    // (Backend @PreAuthorize 见 AgentKbAdminController;Agent 侧同码自校)──

    /** 知识条目分页列表(page/size/kind/keyword 透传)。 */
    public ResponseEntity<String> getKbSources(
            String authorization, Integer page, Integer size, String kind, String keyword) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/kb/sources");
                    if (page != null) {
                        uri.queryParam("page", page);
                    }
                    if (size != null) {
                        uri.queryParam("size", size);
                    }
                    if (kind != null && !kind.isBlank()) {
                        uri.queryParam("kind", kind);
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        uri.queryParam("keyword", keyword);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 新建知识条目(入库即重嵌)。 */
    public ResponseEntity<String> postKbSource(String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/kb/sources")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 条目详情。 */
    public ResponseEntity<String> getKbSource(String authorization, String sourceId) {
        return exchange(restClient.get()
                .uri("/admin/kb/sources/{id}", sourceId)
                .header("Authorization", authorization));
    }

    /** 更新条目(重嵌)。 */
    public ResponseEntity<String> putKbSource(String authorization, String sourceId, Object body) {
        return exchange(restClient.put()
                .uri("/admin/kb/sources/{id}", sourceId)
                .header("Authorization", authorization)
                .body(body));
    }

    /** 启/停用(停用立即退出生检索)。 */
    public ResponseEntity<String> putKbSourceEnabled(
            String authorization, String sourceId, Object body) {
        return exchange(restClient.put()
                .uri("/admin/kb/sources/{id}/enabled", sourceId)
                .header("Authorization", authorization)
                .body(body));
    }

    /** 删除条目(级联 chunks)。 */
    public ResponseEntity<String> deleteKbSource(String authorization, String sourceId) {
        return exchange(restClient.delete()
                .uri("/admin/kb/sources/{id}", sourceId)
                .header("Authorization", authorization));
    }

    /** 重嵌(按已存内容重建向量;换 embedding 模型后逐条补齐)。 */
    public ResponseEntity<String> postKbReembed(String authorization, String sourceId) {
        return exchange(restClient.post()
                .uri("/admin/kb/sources/{id}/reembed", sourceId)
                .header("Authorization", authorization));
    }


    // ── 简历评估代理(RAG 后 B 模块 #135,B6/B7):/admin/evaluation* ——
    // 双道闸 resume:audit(AgentKbAdminController 同款分离权限)──

    /** 评审队列(queue=zero|all;job/评分卡面同源)。 */
    public ResponseEntity<String> getEvaluationQueue(
            String authorization, int cycleId, String queue) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/evaluation/queue");
                    uri.queryParam("cycle_id", cycleId);
                    if (queue != null && !queue.isBlank()) {
                        uri.queryParam("queue", queue);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 触发初筛(job 面,B2)。 */
    public ResponseEntity<String> postEvaluationRun(
            String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/evaluation/run")
                .header("Authorization", authorization)
                .body(body));
    }

    /** job 列表。 */
    public ResponseEntity<String> getEvaluationJobs(
            String authorization, int cycleId, String status) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/evaluation/jobs");
                    uri.queryParam("cycle_id", cycleId);
                    if (status != null && !status.isBlank()) {
                        uri.queryParam("status", status);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 单候选评分卡(面试官场景内只读;Backend 侧放行 interview:evaluate 或 resume:audit)。 */
    public ResponseEntity<String> getEvaluationScorecard(
            String authorization, long resumeId, int cycleId) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/evaluation/scorecard");
                    uri.queryParam("resume_id", resumeId);
                    uri.queryParam("cycle_id", cycleId);
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 采纳(评审本人令牌投一票;令牌透传)。 */
    public ResponseEntity<String> postEvaluationAdopt(
            String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/evaluation/adopt")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 驳回。 */
    public ResponseEntity<String> postEvaluationReject(
            String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/evaluation/reject")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 预置题库(面试官/评审)。 */
    public ResponseEntity<String> getEvaluationQbank(
            String authorization, long resumeId, int cycleId) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/evaluation/qbank");
                    uri.queryParam("resume_id", resumeId);
                    uri.queryParam("cycle_id", cycleId);
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 记录面试官勾选(pick log)。 */
    public ResponseEntity<String> postEvaluationPick(
            String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/evaluation/qbank/pick")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 勾选流水(resume:audit 管理面反哺分析)。 */
    public ResponseEntity<String> getEvaluationPicks(
            String authorization, long resumeId, int cycleId) {
        return exchange(restClient.get()
                .uri(uri -> {
                    uri.path("/admin/evaluation/qbank/picks");
                    uri.queryParam("resume_id", resumeId);
                    uri.queryParam("cycle_id", cycleId);
                    return uri.build();
                })
                .header("Authorization", authorization));
    }

    /** 失败 job 重试(含残留恢复开关)。 */
    public ResponseEntity<String> postEvaluationRetry(
            String authorization, int cycleId, boolean includeStale) {
        return exchange(restClient.post()
                .uri(uri -> {
                    uri.path("/admin/evaluation/jobs/retry");
                    uri.queryParam("cycle_id", cycleId);
                    if (includeStale) {
                        uri.queryParam("include_stale", true);
                    }
                    return uri.build();
                })
                .header("Authorization", authorization));
    }


    // ── 周期复盘代理:/admin/retrospective/* —— 双道闸:触发采集/生成/发布
    // evaluation:run,读取 resume:audit 或 evaluation:run
    // (Backend @PreAuthorize 见 AgentRetrospectiveProxyController)──

    /** 采集并保存指标快照(同步采集,走长超时)。 */
    public ResponseEntity<String> postRetrospectiveSnapshot(String authorization, Object body) {
        return exchange(slowRestClient.post()
                .uri("/admin/retrospective/snapshot")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 读一版快照(version 缺省取最新;给 baselineCycleId 则附差值)。 */
    public ResponseEntity<String> getRetrospectiveSnapshot(
            String authorization, int cycleId, Integer version, Integer baselineCycleId) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("cycle_id", cycleId)
                        .add("version", version)
                        .add("baseline_cycle_id", baselineCycleId)
                        .build(uri, "/admin/retrospective/snapshot"))
                .header("Authorization", authorization));
    }

    /** 一个周期的快照版本列表(不含指标正文)。 */
    public ResponseEntity<String> getRetrospectiveSnapshots(String authorization, int cycleId) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("cycle_id", cycleId)
                        .build(uri, "/admin/retrospective/snapshots"))
                .header("Authorization", authorization));
    }

    /** 生成一版报告草稿(Agent 先同步采一次快照再返回 202,走长超时)。 */
    public ResponseEntity<String> postRetrospectiveRun(String authorization, Object body) {
        return exchange(slowRestClient.post()
                .uri("/admin/retrospective/run")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 读一版报告(version 缺省取最新;草稿可见性由 Agent 按令牌权限裁决)。 */
    public ResponseEntity<String> getRetrospectiveReport(
            String authorization, int cycleId, Integer version) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("cycle_id", cycleId)
                        .add("version", version)
                        .build(uri, "/admin/retrospective/report"))
                .header("Authorization", authorization));
    }

    /** 一个周期的报告版本列表(不含正文)。 */
    public ResponseEntity<String> getRetrospectiveReports(String authorization, int cycleId) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("cycle_id", cycleId)
                        .build(uri, "/admin/retrospective/reports"))
                .header("Authorization", authorization));
    }

    /** 发布一个草稿版本。 */
    public ResponseEntity<String> postRetrospectivePublish(String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/retrospective/publish")
                .header("Authorization", authorization)
                .body(body));
    }


    // ── 知识库运营代理:/admin/kb/ops/* —— 双道闸 kb:manage
    // (Backend @PreAuthorize 见 AgentKbAdminController)──

    /** 启动一次离线分析(Agent 后台执行,返回 202);body 可缺省,由 Agent 取默认天数。 */
    public ResponseEntity<String> postKbOpsRun(String authorization, Object body) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri("/admin/kb/ops/runs")
                .header("Authorization", authorization);
        return exchange(body == null ? spec : spec.body(body));
    }

    /** 最近的分析运行。 */
    public ResponseEntity<String> getKbOpsRuns(String authorization, Integer limit) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("limit", limit)
                        .build(uri, "/admin/kb/ops/runs"))
                .header("Authorization", authorization));
    }

    /** 一次运行的主题清单(runId 缺省取最近一次成功的运行)。 */
    public ResponseEntity<String> getKbOpsTopics(String authorization, String runId, String scope) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("run_id", runId)
                        .add("scope", scope)
                        .build(uri, "/admin/kb/ops/topics"))
                .header("Authorization", authorization));
    }

    /** 草稿分页列表(status 缺省只看待审,all 看全部)。 */
    public ResponseEntity<String> getKbOpsDrafts(
            String authorization, String status, String kind, Integer page, Integer size) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("status", status)
                        .add("kind", kind)
                        .add("page", page)
                        .add("size", size)
                        .build(uri, "/admin/kb/ops/drafts"))
                .header("Authorization", authorization));
    }

    /** 草稿详情。 */
    public ResponseEntity<String> getKbOpsDraft(String authorization, long draftId) {
        return exchange(restClient.get()
                .uri("/admin/kb/ops/drafts/{id}", draftId)
                .header("Authorization", authorization));
    }

    /** 批准前修改草稿(只落在草稿上,不碰知识库)。 */
    public ResponseEntity<String> putKbOpsDraft(String authorization, long draftId, Object body) {
        return exchange(restClient.put()
                .uri("/admin/kb/ops/drafts/{id}", draftId)
                .header("Authorization", authorization)
                .body(body));
    }

    /** 批准草稿并发布到知识库。 */
    public ResponseEntity<String> postKbOpsDraftApprove(String authorization, long draftId) {
        return exchange(restClient.post()
                .uri("/admin/kb/ops/drafts/{id}/approve", draftId)
                .header("Authorization", authorization));
    }

    /** 驳回草稿。 */
    public ResponseEntity<String> postKbOpsDraftReject(String authorization, long draftId) {
        return exchange(restClient.post()
                .uri("/admin/kb/ops/drafts/{id}/reject", draftId)
                .header("Authorization", authorization));
    }


    // ── 初筛校准案例代理:/admin/evaluation/calibration/* —— 双道闸:查看
    // resume:audit,录入/停用/收割 evaluation:run
    // (Backend @PreAuthorize 见 AgentEvaluationProxyController)──

    /** 校准案例列表(可按清单、判定项过滤)。 */
    public ResponseEntity<String> getCalibrationCases(
            String authorization, String checklist, String item,
            Boolean includeRetired, Integer limit, Integer offset) {
        return exchange(restClient.get()
                .uri(uri -> new Query()
                        .add("checklist", checklist)
                        .add("item", item)
                        .add("include_retired", includeRetired)
                        .add("limit", limit)
                        .add("offset", offset)
                        .build(uri, "/admin/evaluation/calibration/cases"))
                .header("Authorization", authorization));
    }

    /** 录入一条种子判例。 */
    public ResponseEntity<String> postCalibrationCase(String authorization, Object body) {
        return exchange(restClient.post()
                .uri("/admin/evaluation/calibration/cases")
                .header("Authorization", authorization)
                .body(body));
    }

    /** 停用一条判例(Agent 侧不删行)。 */
    public ResponseEntity<String> deleteCalibrationCase(String authorization, long caseId) {
        return exchange(restClient.delete()
                .uri("/admin/evaluation/calibration/cases/{id}", caseId)
                .header("Authorization", authorization));
    }

    /** 从已结束周期收割校准案例(同步逐条算向量,走长超时)。 */
    public ResponseEntity<String> postCalibrationHarvest(String authorization, Object body) {
        return exchange(slowRestClient.post()
                .uri("/admin/evaluation/calibration/harvest")
                .header("Authorization", authorization)
                .body(body));
    }

    /**
     * 查询参数构造:空值不拼;值一律走 URI 模板变量,由 UriBuilder 按「值」严格转义。
     *
     * 直接 queryParam(name, value) 会把 value 当模板字面量:+ 原样发出(FastAPI 解成空格)、
     * 花括号被当成模板变量。判定项、清单名这类自由文本("C++ 基础")必须走变量。
     */
    private static final class Query {

        private final Map<String, Object> values = new LinkedHashMap<>();

        Query add(String name, Object value) {
            if (value != null && !(value instanceof String text && text.isBlank())) {
                values.put(name, value);
            }
            return this;
        }

        URI build(UriBuilder uri, String path) {
            uri.path(path);
            values.keySet().forEach(name -> uri.queryParam(name, "{" + name + "}"));
            return uri.build(values);
        }
    }

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> new ResponseEntity<>(
                response.bodyTo(String.class), response.getHeaders(), response.getStatusCode()));
    }
}
