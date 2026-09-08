package club.boyuan.official.integration.agent;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private final RestClient restClient;

    public AgentAdminClient(@Value("${agent.base-url:http://127.0.0.1:8001/api/agent}") String baseUrl) {
        // 钉 HTTP/1.1:默认 HTTP_2 会对明文端点发 h2c 升级,uvicorn(h11) 对
        // 带 body 的升级请求直接判 Invalid HTTP request(night-run E2E 实测),
        // GET 能容忍、POST 不能。Agent 侧是 HTTP/1.1 服务,无需协商。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
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

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> new ResponseEntity<>(
                response.bodyTo(String.class), response.getHeaders(), response.getStatusCode()));
    }
}
