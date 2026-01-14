package com.nju.comment.backend.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "commentCache", key = "#key", unless = "#result == null")
    public CommentResponse getComment(String key) {
        log.info("缓存未命中");
        return null;
    }

    @Override
    @CachePut(value = "commentCache", key = "#key")
    public CommentResponse saveComment(String key, CommentResponse commentResponse) {
        log.info("缓存生成的注释");
        return commentResponse;
    }

    @Override
    @CacheEvict(value = "commentCache", key = "#key")
    public void deleteComment(String key) {
        log.info("已删除缓存的注释");
    }

    @Override
    @CacheEvict(value = "commentCache", allEntries = true)
    public void clearCache() {
        log.info("已清空注释缓存");
    }

    @Override
    public long getCommentCacheSize() {
        CaffeineCache commentCache = (CaffeineCache) cacheManager.getCache("commentCache");
        if (commentCache == null) {
            return 0;
        }
        Cache<Object, Object> nativeCache = commentCache.getNativeCache();
        try {
            return nativeCache.asMap().size();
        } catch (Exception e) {
            log.error("获取注释缓存大小失败", e);
            throw new ServiceException(ErrorCode.CACHE_SIZE_GET_ERROR, e);
        }
    }
}
