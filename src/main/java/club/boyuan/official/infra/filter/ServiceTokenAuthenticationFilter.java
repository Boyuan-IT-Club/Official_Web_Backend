package club.boyuan.official.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 服务间调用鉴权：协同服务（Hocuspocus）回写评价、拉取播种数据时不持有用户 JWT，
 * 改用共享的服务令牌走 {@code /api/internal/**}。
 * <p>
 * 该过滤器只认 {@code /api/internal/} 前缀，令牌未配置或不匹配时不设置任何认证信息，
 * 请求随后会被 Spring Security 以 401 拒绝——即失败时收紧而非放行。
 *
 * @author dhy
 */
@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTokenAuthenticationFilter.class);

    /** 内部服务接口前缀 */
    public static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    /** 服务令牌请求头 */
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    /** 通过校验后授予的角色，配合 @PreAuthorize("hasRole('INTERNAL_SERVICE')") 使用 */
    public static final String INTERNAL_SERVICE_ROLE = "ROLE_INTERNAL_SERVICE";

    @Value("${collab.service-token:}")
    private String serviceToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(SERVICE_TOKEN_HEADER);
        if (serviceToken == null || serviceToken.isBlank()) {
            logger.warn("收到内部接口请求 {} 但未配置 COLLAB_SERVICE_TOKEN，已拒绝", request.getRequestURI());
        } else if (presented != null && constantTimeEquals(serviceToken, presented)) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "collab-service", null, List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_ROLE)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            logger.warn("内部接口 {} 的服务令牌校验失败，来源 {}", request.getRequestURI(), request.getRemoteAddr());
        }

        chain.doFilter(request, response);
    }

    /**
     * 定长时间比较，避免通过响应耗时逐字节猜测令牌。
     */
    private boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
