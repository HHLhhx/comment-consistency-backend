package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheServiceImpl implements CacheService {
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
}
