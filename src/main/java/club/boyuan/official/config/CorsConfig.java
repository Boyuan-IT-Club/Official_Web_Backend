package club.boyuan.official.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的域名
        config.addAllowedOriginPattern("http://localhost:8080");
        config.addAllowedOriginPattern("http://43.143.27.198:8080");
        config.addAllowedOriginPattern("http://localhost:3000");
        config.addAllowedOriginPattern("https://localhost:8080");
        config.addAllowedOriginPattern("https://localhost:3000");
        config.addAllowedOriginPattern("http://127.0.0.1:8080");
        config.addAllowedOriginPattern("http://127.0.0.1:3000");
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许的请求方法
        config.addAllowedMethod("*");
        
        // 允许携带凭证
        config.setAllowCredentials(true);
        
        // 最大预检响应时间
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}