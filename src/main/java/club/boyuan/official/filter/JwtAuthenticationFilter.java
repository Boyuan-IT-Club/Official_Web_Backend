package club.boyuan.official.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.io.IOException;
import java.security.Key;
import java.util.Date;

@Component
public class JwtAuthenticationFilter implements Filter {

    @Value("${jwt.secret}")
    private String secretKey;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 排除登录和注册接口
        String requestURI = httpRequest.getRequestURI();
        if ("/api/auth/login".equals(requestURI) || "/api/auth/register".equals(requestURI) || "/api/auth/send-email-code".equals(requestURI) || "/api/auth/send-sms-code".equals(requestURI)) {
            System.out.println("JWT过滤器: 排除登录/注册接口，请求URI: " + requestURI);
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(httpRequest);
        System.out.println("JWT过滤器: 提取到token: " + (token != null ? token : "不存在"));
        if (token != null) {
            try {
                if (validateToken(token)) {
                    System.out.println("JWT过滤器: token验证通过，继续处理请求");
                    // 验证通过，继续处理请求
                    chain.doFilter(request, response);
                    return;
                }
            } catch (JwtException e) {
                System.out.println("JWT过滤器: token验证失败: " + e.getMessage());
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        System.out.println("JWT过滤器: 请求未携带token，返回401");
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

          // 输出解析到的token信息
        System.out.println("JWT过滤器: 解析到用户ID: " + claims.get("userId"));
        System.out.println("JWT过滤器: 解析到用户名: " + claims.getSubject());
        System.out.println("JWT过滤器: 解析到角色: " + claims.get("roles"));

        Date expiration = claims.getExpiration();
        System.out.println("JWT过滤器: 过期时间: " + expiration);

        return expiration != null && expiration.after(new Date());
    }
}