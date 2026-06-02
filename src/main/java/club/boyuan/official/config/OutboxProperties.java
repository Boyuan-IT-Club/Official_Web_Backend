package club.boyuan.official.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /** 是否启用 Outbox（false 时仍直接发 MQ，便于本地调试） */
    private boolean enabled = true;

    private long relayIntervalMs = 1000L;

    private int batchSize = 50;

    private int maxRetries = 8;
}
