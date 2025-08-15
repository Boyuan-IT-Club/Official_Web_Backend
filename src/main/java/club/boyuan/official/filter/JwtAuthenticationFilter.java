package club.boyuan.official.filter;

import club.boyuan.official.dto.ResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;

import java.io.IOException;
import java.security.Key;
import java.util.*;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String secretKey;

    private final RedisTemplate<String, Object> redisTemplate;

    public JwtAuthenticationFilter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

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
        
        // 明确排除健康检查端点
        if ("/api/health".equals(requestURI) || "/health".equals(requestURI)) {
            chain.doFilter(request, response);
            return;
        }
        
        // 排除以/api/auth开头的所有接口
        if (requestURI.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(httpRequest);
        if (token != null) {
            try {
                if (!validateToken(token)) {
                    logger.warn("token验证失败");
                    httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    ResponseMessage<?> errorResponse = new ResponseMessage<>(BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getCode(), BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getMessage(), null);
                    new ObjectMapper().writeValue(httpResponse.getWriter(), errorResponse);
                    return;
                }
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(getSigningKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                
                // 从claims中获取角色信息
                List<String> roles = (List<String>) claims.get("roles");
                if (roles == null) {
                    roles = new ArrayList<>();
                }

                // 转换角色为Spring Security权限
                Collection<GrantedAuthority> authorities = new ArrayList<>();
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }

                // 创建认证对象并设置权限
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, authorities);
                
                // 设置认证信息到上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                chain.doFilter(request, response);
                return;
            } catch (JwtException e) {
                logger.warn("token验证失败: {}", e.getMessage());
                httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
                httpResponse.setContentType("application/json;charset=UTF-8");
                ResponseMessage<?> errorResponse = new ResponseMessage<>(BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getCode(), BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getMessage(), null);
                try {
                    new ObjectMapper().writeValue(httpResponse.getWriter(), errorResponse);
                } catch (IOException ex) {
                    logger.error("写入响应时发生错误", ex);
                }
                return;
            }
        }
        
        // 如果没有token，让Spring Security根据配置决定是否允许访问
        // 不再记录警告日志，因为有些端点可能确实不需要token
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        // 检查令牌是否在黑名单中
        if (Boolean.TRUE.equals(redisTemplate.hasKey("jwt:blacklist:" + token))) {
            logger.warn("token已被注销");
            throw new BusinessException(BusinessExceptionEnum.JWT_HAS_BEEN_LOGGED_OUT);
        }
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        Date expiration = claims.getExpiration();

        return expiration != null && expiration.after(new Date());
    }
}