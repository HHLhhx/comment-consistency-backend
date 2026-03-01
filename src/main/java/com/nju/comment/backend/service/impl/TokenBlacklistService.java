package com.nju.comment.backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT令牌黑名单服务
 * <p>
 * 基于 Redis 管理已登出的 JWT 令牌，利用 Redis 的 TTL 自动过期机制清理数据。
 * 支持多实例部署时的黑名单共享。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "cc:token:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 将令牌加入黑名单
     *
     * @param token      JWT令牌
     * @param expiration 令牌的过期时间（到期后 Redis 自动删除）
     */
    public void blacklist(String token, Date expiration) {
        long ttlMs = expiration.getTime() - System.currentTimeMillis();
        if (ttlMs <= 0) {
            log.debug("令牌已过期，无需加入黑名单");
            return;
        }
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", ttlMs, TimeUnit.MILLISECONDS);
        log.debug("令牌已加入黑名单，TTL={}ms", ttlMs);
    }

    /**
     * 检查令牌是否在黑名单中
     *
     * @param token JWT令牌
     * @return true表示令牌已被拉黑（已登出）
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(key);
    }
}
