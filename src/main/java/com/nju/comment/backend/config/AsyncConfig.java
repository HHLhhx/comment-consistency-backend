package com.nju.comment.backend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
@ConfigurationProperties(prefix = "app.thread-pool")
@Data
public class AsyncConfig implements AsyncConfigurer {

    private AsyncConfigItem llmPool;

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
            thread.setName("llm-task-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("线程 {} 执行异常", t.getName(), e));
            return thread;
        }));

        executor.initialize();

        logThreadPoolStatus(executor, "LLM Task Executor");

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    private void logThreadPoolStatus(ThreadPoolTaskExecutor executor, String name) {
        log.debug("{} 配置: 核心线程数={}, 最大线程数={}, 队列容量={}, 保活时间={}",
                name,
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity(),
                executor.getKeepAliveSeconds());
    }

    @Slf4j
    static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("异步方法执行异常: 方法={}, 参数={}", method.getName(), params, ex);
        }
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
