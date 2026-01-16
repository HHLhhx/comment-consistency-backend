package com.nju.comment.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "app.cache")
@Data
public class CacheConfig {

    private CacheConfigItem comment;
    private CacheConfigItem modelsList;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        if (comment != null) {
            Caffeine<Object, Object> commentCaffeine = Caffeine.newBuilder()
                    .maximumSize(comment.getMaxSize())
                    .expireAfterWrite(comment.getTtl(), TimeUnit.SECONDS)
                    .recordStats();

            cacheManager.registerCustomCache("commentCache", commentCaffeine.build());
        }

        if (modelsList != null) {
            Caffeine<Object, Object> modelsListCaffeine = Caffeine.newBuilder()
                    .maximumSize(modelsList.getMaxSize())
                    .expireAfterWrite(modelsList.getTtl(), TimeUnit.SECONDS)
                    .recordStats();

            cacheManager.registerCustomCache("modelsListCache", modelsListCaffeine.build());
        }

        return cacheManager;
    }

    @Data
    public static class CacheConfigItem {
        private int maxSize;
        private int ttl;
    }
}
