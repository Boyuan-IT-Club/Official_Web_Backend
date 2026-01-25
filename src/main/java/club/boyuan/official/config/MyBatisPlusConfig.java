package club.boyuan.official.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置类
 * 
 * 此配置类用于设置 MyBatis-Plus 的全局插件
 * 主要功能包括分页插件等
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 19:31
 * @since 2026
 */
@Configuration
public class MyBatisPlusConfig {

    // MybatisPlus在执行分页操作时,会被该拦截器拦截
    // 拦截器的作用 动态拼接where条件!!!
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MARIADB));
        return interceptor;
    }
}