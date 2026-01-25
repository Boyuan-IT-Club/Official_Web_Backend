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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // 检查是否需要跳过认证
        if (shouldSkipAuthentication(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(httpRequest);
        if (token != null) {
            try {
                // 验证并解析token
                if (!validateToken(token)) {
                    logger.warn("token验证失败");
                    handleAuthenticationException(httpResponse, new JwtException("Invalid token"));
                    return;
                }
                
                // 解析token获取claims
                Claims claims = parseTokenClaims(token);
                
                // 设置认证信息
                setAuthentication(claims);
                
                chain.doFilter(request, response);
                return;
            } catch (Exception e) {
                logger.warn("认证失败: {}", e.getMessage());
                handleAuthenticationException(httpResponse, e);
                return;
            }
        }
        
        // 如果没有token，让Spring Security根据配置决定是否允许访问
        chain.doFilter(request, response);
    }

    /**
     * 判断是否需要跳过认证
     * @param request HttpServletRequest
     * @return boolean
     */
    private boolean shouldSkipAuthentication(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // 明确排除健康检查端点
        if ("/api/health".equals(requestURI) || "/health".equals(requestURI)) {
            return true;
        }
        // 排除以/api/auth开头的所有接口
        return requestURI.startsWith("/api/auth");
    }

    /**
     * 解析token获取claims
     * @param token JWT token
     * @return Claims
     */
    private Claims parseTokenClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从claims构建权限列表
     * @param claims Claims对象
     * @return Collection<GrantedAuthority>
     */
    private Collection<GrantedAuthority> buildAuthorities(Claims claims) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        // 从claims中获取角色名称
        List<String> roleNames = (List<String>) claims.get("roleNames");
        if (roleNames != null) {
            for (String roleName : roleNames) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
            }
        }
        
        // 从claims中获取权限码信息
        List<String> permissionCodes = (List<String>) claims.get("permissionCodes");
        if (permissionCodes != null) {
            for (String permissionCode : permissionCodes) {
                authorities.add(new SimpleGrantedAuthority(permissionCode));
            }
        }
        
        return authorities;
    }

    /**
     * 设置认证信息到上下文
     * @param claims Claims对象
     */
    private void setAuthentication(Claims claims) {
        // 从claims构建权限列表
        Collection<GrantedAuthority> authorities = buildAuthorities(claims);
        
        // 创建认证对象并设置权限
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, authorities);
        
        // 设置认证信息到上下文
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 处理认证异常
     * @param response HttpServletResponse
     * @param e Exception
     */
    private void handleAuthenticationException(HttpServletResponse response, Exception e) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        ResponseMessage<?> errorResponse = new ResponseMessage<>(BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getCode(), 
                BusinessExceptionEnum.JWT_VERIFICATION_FAILED.getMessage(), null);
        try {
            objectMapper.writeValue(response.getWriter(), errorResponse);
        } catch (IOException ex) {
            logger.error("写入响应时发生错误", ex);
            throw ex;
        }
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
        Claims claims = parseTokenClaims(token);
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.after(new Date());
    }
}