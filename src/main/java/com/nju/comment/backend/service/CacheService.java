package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.response.CommentResponse;

import java.util.List;

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
     * 获取缓存的模型列表
     */
    List<String> getModelsList(String key);

    /**
     * 保存模型列表到缓存
     */
    List<String> saveModelsList(String key, List<String> modelsList);

    /**
     * 删除缓存
     */
    void deleteComment(String key);

    /**
     * 清空缓存
     */
    void clearCommentCache();
}
