package com.nju.comment.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.thread-pool.llm.core-size:20}")
    private int llmCorePoolSize;

    @Value("${app.thread-pool.llm.max-size:50}")
    private int llmMaxPoolSize;

    @Value("${app.thread-pool.llm.queue-capacity:200}")
    private int llmQueueCapacity;

    @Value("${app.thread-pool.llm.keep-alive:60}")
    private int llmKeepAliveSeconds;

    @Value("${app.thread-pool.llm.thread-name-prefix:llm-executor-}")
    private String llmThreadNamePrefix;

    @Bean(name = "llmTaskExecutor")
    public ThreadPoolTaskExecutor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(llmCorePoolSize);
        executor.setMaxPoolSize(llmMaxPoolSize);
        executor.setQueueCapacity(llmQueueCapacity);
        executor.setKeepAliveSeconds(llmKeepAliveSeconds);
        executor.setThreadNamePrefix(llmThreadNamePrefix);

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


    @Value("${app.thread-pool.health-check.core-size:2}")
    private int healthCheckCorePoolSize;

    @Value("${app.thread-pool.health-check.max-size:5}")
    private int healthCheckMaxPoolSize;

    @Value("${app.thread-pool.health-check.queue-capacity:50}")
    private int healthCheckQueueCapacity;

    @Value("${app.thread-pool.health-check.keep-alive:30}")
    private int healthCheckKeepAliveSeconds;

    @Value("${app.thread-pool.health-check.thread-name-prefix:health-check-}")
    private String healthCheckThreadNamePrefix;

    @Bean(name = "healthCheckExecutor")
    public ThreadPoolTaskExecutor healthCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(healthCheckCorePoolSize);
        executor.setMaxPoolSize(healthCheckMaxPoolSize);
        executor.setQueueCapacity(healthCheckQueueCapacity);
        executor.setKeepAliveSeconds(healthCheckKeepAliveSeconds);
        executor.setThreadNamePrefix(healthCheckThreadNamePrefix);
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
}
