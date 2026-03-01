package com.nju.comment.backend.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "app.cache")
@Data
public class CacheConfig {

    private CacheConfigItem comment;
    private CacheConfigItem modelsList;
    private CacheConfigItem tokenBlacklist;

    /**
     * Redis序列化用的ObjectMapper
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    /**
     * RedisTemplate — 供 TokenBlacklistService 等需要直接操作 Redis 的场景使用
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis缓存管理器 — 用于注释缓存（支持用户隔离）和令牌黑名单
     */
    @Bean("redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // 默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(30));

        // 各缓存分别配置
        Map<String, RedisCacheConfiguration> cacheConfigMap = new HashMap<>();

        // 注释缓存 — key 格式: commentCache::userId:cacheKey
        if (comment != null) {
            cacheConfigMap.put("commentCache", defaultConfig
                    .entryTtl(Duration.ofSeconds(comment.getTtl()))
                    .prefixCacheNameWith("cc:"));
        }

        // 令牌黑名单缓存
        if (tokenBlacklist != null) {
            cacheConfigMap.put("tokenBlacklist", defaultConfig
                    .entryTtl(Duration.ofSeconds(tokenBlacklist.getTtl()))
                    .prefixCacheNameWith("cc:"));
        }

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigMap)
                .transactionAware()
                .build();
    }

    /**
     * Caffeine本地缓存管理器 — 用于模型列表等无需用户隔离的高频读取场景
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        if (modelsList != null) {
            Caffeine<Object, Object> modelsListCaffeine = Caffeine.newBuilder()
                    .maximumSize(modelsList.getMaxSize())
                    .expireAfterWrite(modelsList.getTtl(), TimeUnit.SECONDS)
                    .recordStats();
            cacheManager.registerCustomCache("modelCache", modelsListCaffeine.build());
        }

        return cacheManager;
    }

    /**
     * 组合缓存管理器 — 先查Redis，再查Caffeine
     */
    @Bean("compositeCacheManager")
    public CacheManager compositeCacheManager(
            CacheManager redisCacheManager, CacheManager caffeineCacheManager) {
        CompositeCacheManager composite = new CompositeCacheManager(redisCacheManager, caffeineCacheManager);
        composite.setFallbackToNoOpCache(false);
        return composite;
    }

    @Data
    public static class CacheConfigItem {
        private int maxSize;
        private int ttl;
    }
}
