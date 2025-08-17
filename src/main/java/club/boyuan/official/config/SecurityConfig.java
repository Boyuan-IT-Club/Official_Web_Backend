package club.boyuan.official.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import club.boyuan.official.filter.JwtAuthenticationFilter;

/**
 * Spring Security核心配置类
 * 完全禁用Spring Boot的默认安全配置，使用自定义JWT认证机制
 * 解决"Using generated security password"警告问题
 */
@Configuration
@EnableWebSecurity
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
                .requestMatchers("/api/public/**").permitAll()
                // 允许静态资源访问
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )
            // 配置会话管理为无状态
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 添加JWT认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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