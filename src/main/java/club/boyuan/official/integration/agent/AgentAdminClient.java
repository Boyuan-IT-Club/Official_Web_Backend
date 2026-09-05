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
        HttpClient httpClient = HttpClient.newBuilder()
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

    private ResponseEntity<String> exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> new ResponseEntity<>(
                response.bodyTo(String.class), response.getHeaders(), response.getStatusCode()));
    }
}
