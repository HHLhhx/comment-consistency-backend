package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务实现
 * <p>
 * 注释缓存使用 RedisTemplate 直接操作，支持访问时刷新 TTL。
 * 模型列表缓存使用 Caffeine（expireAfterAccess），无需用户隔离。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private static final String COMMENT_KEY_PREFIX = "cc:comment:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.comment.ttl:3600}")
    private long commentTtlSeconds;

    // ==================== 注释缓存（Redis + TTL 续期） ====================

    @Override
    public String getComment(String key) {
        String redisKey = COMMENT_KEY_PREFIX + key;
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            // 命中缓存，刷新 TTL
            redisTemplate.expire(redisKey, commentTtlSeconds, TimeUnit.SECONDS);
            log.debug("注释缓存命中并刷新TTL, key={}", key);
            return (String) value;
        }
        log.debug("注释缓存未命中, key={}", key);
        return null;
    }

    @Override
    public String saveComment(String key, String comment) {
        String redisKey = COMMENT_KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, comment, commentTtlSeconds, TimeUnit.SECONDS);
        log.debug("缓存生成的注释, key={}, ttl={}s", key, commentTtlSeconds);
        return comment;
    }

    // ==================== 模型缓存（Caffeine） ====================

    @Override
    @Cacheable(value = "modelCache", key = "#key", unless = "#result == null",
            cacheManager = "caffeineCacheManager")
    public List<String> getModelsList(String key) {
        log.debug("模型缓存未命中, key={}", key);
        return null;
    }

    @Override
    @CachePut(value = "modelCache", key = "#key",
            cacheManager = "caffeineCacheManager")
    public List<String> saveModelsList(String key, List<String> modelsList) {
        log.debug("缓存模型列表, key={}", key);
        return modelsList;
    }
}
