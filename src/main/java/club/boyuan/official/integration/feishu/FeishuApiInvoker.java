package club.boyuan.official.integration.feishu;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * 带熔断、指数退避的飞书 HTTP 调用封装。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuApiInvoker {

    private final FeishuApiCircuitBreaker circuitBreaker;
    private final FeishuApiRetryPolicy retryPolicy;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public String get(String url, String bearerToken) {
        return execute(() -> restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .body(String.class));
    }

    public String postJson(String url, String bearerToken, Object body) {
        return execute(() -> {
            var spec = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            if (org.springframework.util.StringUtils.hasText(bearerToken)) {
                spec = spec.header("Authorization", "Bearer " + bearerToken);
            }
            return spec.body(body).retrieve().body(String.class);
        });
    }

    private String execute(HttpCall call) {
        circuitBreaker.checkOpen();
        int maxAttempts = retryPolicy.maxAttempts();
        Exception last = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                String body = call.run();
                JsonNode root = objectMapper.readTree(body);
                if (retryPolicy.isRetryableFeishuResponse(root) && attempt < maxAttempts - 1) {
                    log.warn("飞书 API 限流/可重试 code={}, attempt={}/{}", root.path("code").asInt(),
                            attempt + 1, maxAttempts);
                    retryPolicy.sleepBackoff(attempt);
                    continue;
                }
                circuitBreaker.recordSuccess();
                return body;
            } catch (HttpStatusCodeException ex) {
                last = ex;
                circuitBreaker.recordFailure();
                if (retryPolicy.isRetryableHttp(ex) && attempt < maxAttempts - 1) {
                    log.warn("飞书 HTTP {} 退避重试 attempt={}/{}", ex.getStatusCode().value(),
                            attempt + 1, maxAttempts);
                    try {
                        retryPolicy.sleepBackoff(attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书请求被中断");
                    }
                    continue;
                }
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED,
                        "飞书 HTTP 错误: " + ex.getStatusCode().value());
            } catch (BusinessException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书请求被中断");
            } catch (Exception ex) {
                last = ex;
                circuitBreaker.recordFailure();
                if (attempt < maxAttempts - 1) {
                    try {
                        retryPolicy.sleepBackoff(attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书请求被中断");
                    }
                    continue;
                }
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书请求失败: " + ex.getMessage());
            }
        }

        circuitBreaker.recordFailure();
        String msg = last != null ? last.getMessage() : "未知错误";
        throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书请求重试耗尽: " + msg);
    }

    @FunctionalInterface
    private interface HttpCall {
        String run() throws Exception;
    }
}
