package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 缓存服务实现
 * <p>
 * 注释缓存使用 Redis（redisCacheManager），key 中包含用户标识以实现用户间隔离。
 * 模型列表缓存使用 Caffeine（caffeineCacheManager），无需用户隔离。
 */
@Service
@Slf4j
public class CacheServiceImpl implements CacheService {

    @Override
    @Cacheable(value = "commentCache", key = "#key", unless = "#result == null",
            cacheManager = "redisCacheManager")
    public CommentResponse getComment(String key) {
        log.debug("注释缓存未命中, key={}", key);
        return null;
    }

    @Override
    @CachePut(value = "commentCache", key = "#key",
            cacheManager = "redisCacheManager")
    public CommentResponse saveComment(String key, CommentResponse commentResponse) {
        log.debug("缓存生成的注释, key={}", key);
        return commentResponse;
    }

    @Override
    @CacheEvict(value = "commentCache", key = "#key",
            cacheManager = "redisCacheManager")
    public void deleteComment(String key) {
        log.debug("已删除缓存, key={}", key);
    }

    @Override
    @CacheEvict(value = "commentCache", allEntries = true,
            cacheManager = "redisCacheManager")
    public void clearCommentCache() {
        log.info("已清空注释缓存");
    }

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

    /**
     * 获取当前登录用户名，用于构建用户隔离的缓存key
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
