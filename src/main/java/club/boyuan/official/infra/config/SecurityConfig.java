package club.boyuan.official.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.infra.filter.JwtAuthenticationFilter;

/**
 * Spring Security核心配置类
 * 完全禁用Spring Boot的默认安全配置，使用自定义JWT认证机制
 * 解决"Using generated security password"警告问题
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 配置安全过滤链
     * @param http HttpSecurity对象
     * @return SecurityFilterChain实例
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 禁用 CSRF 保护
                .csrf(csrf -> csrf.disable())
                // 禁用默认的表单登录
                .formLogin(form -> form.disable())
                // 禁用默认的HTTP基本认证
                .httpBasic(basic -> basic.disable())
                // 配置请求授权规则
                .authorizeHttpRequests(authz -> authz
                        // 允许公开访问的接口
                        .requestMatchers("/api/auth/**", "/api/health", "/api/health/**", "/health").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // 允许访问上传的文件
                        .requestMatchers("/uploads/**").permitAll()
                        // 允许静态资源访问
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                // 未认证/权限不足的统一处理：匿名访问受保护资源返回 401，已认证但权限不足返回 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                // 配置会话管理为无状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 未认证访问受保护资源时返回 401（而非 Spring Security 默认的 403）
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, response, authException) -> writeJson(objectMapper, response,
                HttpStatus.UNAUTHORIZED,
                BusinessExceptionEnum.USER_AUTHENTICATION_FAILED.getCode(),
                BusinessExceptionEnum.USER_AUTHENTICATION_FAILED.getMessage());
    }

    /**
     * 已认证但权限不足时返回 403
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, response, accessDeniedException) -> writeJson(objectMapper, response,
                HttpStatus.FORBIDDEN,
                BusinessExceptionEnum.PERMISSION_DENIED.getCode(),
                BusinessExceptionEnum.PERMISSION_DENIED.getMessage());
    }

    private void writeJson(ObjectMapper objectMapper, HttpServletResponse response,
                           HttpStatus status, Integer code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), new ResponseMessage<>(code, message, null));
    }

    /**
     * 配置CORS跨域资源共享
     * @return CorsConfigurationSource实例
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的来源(origin)。注意：即使前端走同源 /api（nginx 反代），
        // 浏览器对 POST 等仍会带 Origin 头，Spring 会校验，故部署站点 origin 必须在此白名单内。
        // 生产站点入口
        configuration.addAllowedOriginPattern("http://8.159.153.140");
        configuration.addAllowedOriginPattern("https://8.159.153.140");
        configuration.addAllowedOriginPattern("http://8.159.150.156");
        configuration.addAllowedOriginPattern("https://8.159.150.156");
        configuration.addAllowedOriginPattern("http://official.boyuan.club");
        configuration.addAllowedOriginPattern("https://official.boyuan.club");
        // 本地开发 / 直连
        configuration.addAllowedOriginPattern("http://localhost:3000");
        configuration.addAllowedOriginPattern("https://localhost:3000");
        configuration.addAllowedOriginPattern("http://127.0.0.1:3000");
        configuration.addAllowedOriginPattern("http://localhost:8080");
        configuration.addAllowedOriginPattern("https://localhost:8080");
        configuration.addAllowedOriginPattern("http://127.0.0.1:8080");

        // 允许的请求头
        configuration.addAllowedHeader("*");

        // 允许的请求方法
        configuration.addAllowedMethod("*");

        // 允许携带凭证
        configuration.setAllowCredentials(true);

        // 最大预检响应时间
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 配置BCrypt密码编码器
     * @return BCryptPasswordEncoder实例
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}