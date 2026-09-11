package club.boyuan.official.infra.config;

import club.boyuan.official.infra.ratelimit.IdentityRateLimitRuleConfig;
import club.boyuan.official.infra.ratelimit.RateLimitRuleConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private List<RateLimitRuleConfig> rules = new ArrayList<>();

    /**
     * 按业务身份（当前只有邮箱）限流的规则，key 为规则名。
     * 和 rules 是两个维度：rules 按 IP 兜住「一个出口打整站」，
     * identity-rules 按邮箱兜住「一个人反复刷」。校园 NAT 下
     * 一栋楼共用一个出口 IP，只靠 IP 维度要么限太松要么误伤一片。
     */
    private Map<String, IdentityRateLimitRuleConfig> identityRules = new LinkedHashMap<>();
}
