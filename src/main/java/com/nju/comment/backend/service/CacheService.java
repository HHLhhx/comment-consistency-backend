package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.response.CommentResponse;

public interface CacheService {

    /**
     * 获取缓存的注释
     */
    CommentResponse getComment(String key);

    /**
     * 保存注释到缓存
     */
    CommentResponse saveComment(String key, CommentResponse commentResponse);


    /**
     * 删除缓存的注释
     */
    void deleteComment(String key);

    /**
     * 清空缓存
     */
    void clearCache();

    /**
     * 获取缓存对数
     */
    long getCommentCacheSize();
}
