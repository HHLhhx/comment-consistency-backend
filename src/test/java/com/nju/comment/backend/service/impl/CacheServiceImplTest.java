package com.nju.comment.backend.service.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nju.comment.backend.dto.response.CommentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CacheServiceImplTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private CacheServiceImpl cacheService;

    Cache cache;

    @BeforeEach
    void setup() {
        cache = new CaffeineCache("commentCache", Caffeine.newBuilder().build());
        when(cacheManager.getCache("commentCache")).thenReturn(cache);

        cacheService.saveComment("key", CommentResponse.builder().generatedComment("cached comment").build());
    }

    @Test
    void get_not_existed_comment_from_cache() {
        CommentResponse result = cacheService.getComment("key1");
        assertNull(result);
        assertEquals(1, cacheService.getCommentCacheSize());
    }

    @Test
    void save_comment_to_cache() {
        CommentResponse commentResponse = CommentResponse.builder().generatedComment("test").build();

        CommentResponse result = cacheService.saveComment("test", commentResponse);

        assertNotNull(result);
        assertEquals(commentResponse, result);
        assertEquals(2, cacheService.getCommentCacheSize());
    }

    @Test
    void delete_comment_from_cache() {
        cacheService.deleteComment("key");

        CommentResponse result = cacheService.getComment("key");

        assertNull(result);
        assertEquals(0, cacheService.getCommentCacheSize());
    }

    @Test
    void clear_all_cache_entries() {
        cacheService.clearCache();

        CommentResponse result = cacheService.getComment("key");

        assertNull(result);
        assertEquals(0, cacheService.getCommentCacheSize());
    }

    @Test
    void get_existed_comment_from_cache() {
        CommentResponse cachedResponse = CommentResponse.builder().generatedComment("test comment").build();

        cacheService.saveComment("key2", cachedResponse);
        CommentResponse result = cacheService.getComment("key2");

        assertNotNull(result);
        assertEquals(cachedResponse, result);
        assertEquals("test comment", result.getGeneratedComment());
        assertEquals(2, cacheService.getCommentCacheSize());
    }

    @Test
    void get_cache_size() {
        long size = cacheService.getCommentCacheSize();
        assertEquals(1, size);

        cacheService.saveComment("key2", CommentResponse.builder().generatedComment("another comment").build());
        size = cacheService.getCommentCacheSize();
        assertEquals(2, size);
    }
}
