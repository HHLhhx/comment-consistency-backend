package com.nju.comment.backend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
@ConfigurationProperties(prefix = "app.thread-pool")
@Data
public class AsyncConfig implements AsyncConfigurer {

    private AsyncConfigItem llmPool;

    private AsyncConfigItem healthCheckPool;

    @Bean(name = "llmTaskExecutor")
    public ThreadPoolTaskExecutor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(llmPool.getCoreSize());
        executor.setMaxPoolSize(llmPool.getMaxSize());
        executor.setQueueCapacity(llmPool.getQueueCapacity());
        executor.setKeepAliveSeconds(llmPool.getKeepAlive());
        executor.setThreadNamePrefix(llmPool.getThreadNamePrefix());

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setThreadFactory((r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("线程 {} 执行异常", t.getName(), e));
            return thread;
        }));

        executor.initialize();

        logThreadPoolStatus(executor, "LLM Task Executor");

        return executor;
    }

    @Bean(name = "healthCheckExecutor")
    public ThreadPoolTaskExecutor healthCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(healthCheckPool.getCoreSize());
        executor.setMaxPoolSize(healthCheckPool.getMaxSize());
        executor.setQueueCapacity(healthCheckPool.getQueueCapacity());
        executor.setKeepAliveSeconds(healthCheckPool.getKeepAlive());
        executor.setThreadNamePrefix(healthCheckPool.getThreadNamePrefix());
        executor.initialize();

        logThreadPoolStatus(executor, "Health Check Executor");

        return  executor;
    }

    private void logThreadPoolStatus(ThreadPoolTaskExecutor executor, String name) {
        log.info("{} 配置: 核心线程数={}, 最大线程数={}, 队列容量={}, 保活时间={}",
                name,
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity(),
                executor.getKeepAliveSeconds());
    }

    @Data
    public static class AsyncConfigItem {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;
        private int keepAlive;
        private String threadNamePrefix;
    }
}
