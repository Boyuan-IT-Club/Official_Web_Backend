package club.boyuan.official.infra.ratelimit;

import club.boyuan.official.infra.config.RateLimitProperties;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 应用层「网关式」限流：在请求进入 Controller 前按规则拦截。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties rateLimitProperties;
    private final RateLimitService rateLimitService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        for (RateLimitRuleConfig rule : rateLimitProperties.getRules()) {
            if (!method.equalsIgnoreCase(rule.getMethod())) {
                continue;
            }
            if (!pathMatcher.match(rule.getPattern(), path)) {
                continue;
            }

            String identity = resolveIdentity(request, rule.getKeyType());
            boolean allowed = rateLimitService.tryAcquire(
                    rule.getName(),
                    identity,
                    rule.getLimit(),
                    rule.getWindowSeconds());
            if (!allowed) {
                response.setHeader("Retry-After", String.valueOf(rule.getWindowSeconds()));
                throw new BusinessException(BusinessExceptionEnum.TOO_MANY_REQUESTS);
            }
            break;
        }
        return true;
    }

    private String resolveIdentity(HttpServletRequest request, RateLimitKeyType keyType) {
        if (keyType == RateLimitKeyType.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && StringUtils.hasText(auth.getName())
                    && !"anonymousUser".equals(auth.getName())) {
                return "user:" + auth.getName();
            }
        }
        if (keyType == RateLimitKeyType.REMOTE_ADDR) {
            return "remote:" + request.getRemoteAddr();
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
