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

    @Value("${task.execution.pool.llm-task-executor.core-size:20}")
    private int llmCorePoolSize;

    @Value("${task.execution.pool.llm-task-executor.max-size:50}")
    private int llmMaxPoolSize;

    @Value("${task.execution.pool.llm-task-executor.queue-capacity:1000}")
    private int llmQueueCapacity;

    @Value("${task.execution.pool.llm-task-executor.keep-alive:60}")
    private int llmKeepAliveSeconds;

    @Value("${task.execution.pool.llm-task-executor.thread-name-prefix:llm-executor-}")
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


    @Value("${task.execution.pool.health-check-executor.core-size:2}")
    private int healthCheckCorePoolSize;

    @Value("${task.execution.pool.health-check-executor.max-size:5}")
    private int healthCheckMaxPoolSize;

    @Value("${task.execution.pool.health-check-executor.queue-capacity:100}")
    private int healthCheckQueueCapacity;

    @Value("${task.execution.pool.health-check-executor.keep-alive:30}")
    private int healthCheckKeepAliveSeconds;

    @Value("${task.execution.pool.health-check-executor.thread-name-prefix:health-check-}")
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
