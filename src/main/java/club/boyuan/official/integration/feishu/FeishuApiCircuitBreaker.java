package club.boyuan.official.integration.feishu;

import club.boyuan.official.infra.config.FeishuProperties;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 飞书 Open API 简易熔断：连续失败达阈值后短暂拒绝请求。
 */
@Component
public class FeishuApiCircuitBreaker {

    private final FeishuProperties feishuProperties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant openUntil = Instant.EPOCH;

    public FeishuApiCircuitBreaker(FeishuProperties feishuProperties) {
        this.feishuProperties = feishuProperties;
    }

    public void checkOpen() {
        if (Instant.now().isBefore(openUntil)) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED,
                    "飞书 API 熔断中，请稍后重试");
        }
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntil = Instant.EPOCH;
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, feishuProperties.getCircuitFailureThreshold())) {
            openUntil = Instant.now().plusSeconds(Math.max(1, feishuProperties.getCircuitOpenSeconds()));
            consecutiveFailures.set(0);
        }
    }
}
