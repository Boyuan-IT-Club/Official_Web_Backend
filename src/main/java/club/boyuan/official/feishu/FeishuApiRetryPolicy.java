package club.boyuan.official.feishu;

import club.boyuan.official.config.FeishuProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 飞书 API 可重试判定与指数退避。
 */
@Component
@RequiredArgsConstructor
public class FeishuApiRetryPolicy {

    private final FeishuProperties feishuProperties;

    public int maxAttempts() {
        return Math.max(1, feishuProperties.getApiMaxRetries() + 1);
    }

    public long backoffMs(int attemptIndex) {
        long initial = Math.max(50L, feishuProperties.getApiInitialBackoffMs());
        long max = Math.max(initial, feishuProperties.getApiMaxBackoffMs());
        long backoff = initial * (1L << Math.min(attemptIndex, 10));
        return Math.min(backoff, max);
    }

    public boolean isRetryableHttp(HttpStatusCodeException ex) {
        int status = ex.getStatusCode().value();
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    public boolean isRetryableFeishuCode(int code, String msg) {
        if (code == 99991400 || code == 230020) {
            return true;
        }
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("frequency") || lower.contains("limit") || lower.contains("限流")
                    || lower.contains("rate") || lower.contains("too many")) {
                return true;
            }
        }
        return false;
    }

    public boolean isRetryableFeishuResponse(JsonNode root) {
        if (root == null) {
            return false;
        }
        int code = root.path("code").asInt(-1);
        if (code == 0) {
            return false;
        }
        return isRetryableFeishuCode(code, root.path("msg").asText(""));
    }

    public void sleepBackoff(int attemptIndex) throws InterruptedException {
        Thread.sleep(backoffMs(attemptIndex));
    }
}
