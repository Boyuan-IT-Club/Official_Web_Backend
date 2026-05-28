package club.boyuan.official.config;

import club.boyuan.official.ratelimit.RateLimitRuleConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private List<RateLimitRuleConfig> rules = new ArrayList<>();
}
