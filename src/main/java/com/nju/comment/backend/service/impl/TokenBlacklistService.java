package com.nju.comment.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT令牌黑名单服务
 * <p>
 * 用于管理已登出的JWT令牌，防止已登出用户继续使用旧令牌访问系统。
 * 使用内存存储，定时清理过期令牌以避免内存泄漏。
 */
@Service
@Slf4j
public class TokenBlacklistService {

    /**
     * 黑名单存储：token -> 过期时间
     * 使用ConcurrentHashMap保证线程安全
     */
    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * 将令牌加入黑名单
     *
     * @param token      JWT令牌
     * @param expiration 令牌的过期时间（到期后自动从黑名单中移除）
     */
    public void blacklist(String token, Date expiration) {
        blacklistedTokens.put(token, expiration);
        log.debug("令牌已加入黑名单，将于 {} 过期后自动清理", expiration);
    }

    /**
     * 检查令牌是否在黑名单中
     *
     * @param token JWT令牌
     * @return true表示令牌已被拉黑（已登出）
     */
    public boolean isBlacklisted(String token) {
        return blacklistedTokens.containsKey(token);
    }

    /**
     * 定时清理过期的黑名单令牌（每小时执行一次）
     * 已过期的令牌本身就不能通过JWT校验，无需继续保留在黑名单中
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanExpiredTokens() {
        Date now = new Date();
        int beforeSize = blacklistedTokens.size();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().before(now));
        int removed = beforeSize - blacklistedTokens.size();
        if (removed > 0) {
            log.info("清理过期黑名单令牌: 移除{}个，剩余{}个", removed, blacklistedTokens.size());
        }
    }
}
