package club.boyuan.official.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 飞书「按地点分桶」并行写入用的线程池。
 * 线程数与 {@link FeishuProperties#getParallelBucketConcurrency()} 一致。
 */
@Configuration
public class FeishuExecutorConfig {

    @Bean(name = "feishuBucketExecutor")
    public Executor feishuBucketExecutor(FeishuProperties feishuProperties) {
        int concurrency = Math.max(1, feishuProperties.getParallelBucketConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("feishu-bucket-");
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(concurrency * 4);
        executor.initialize();
        return executor;
    }
}
