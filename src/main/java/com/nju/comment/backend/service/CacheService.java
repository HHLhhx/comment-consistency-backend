package com.nju.comment.backend.service;

import java.util.List;

public interface CacheService {

    /**
     * 获取缓存的注释
     */
    String getComment(String key);

    /**
     * 保存注释到缓存
     */
    String saveComment(String key, String comment);

    /**
     * 获取缓存的模型列表
     */
    List<String> getModelsList(String key);

    /**
     * 保存模型列表到缓存
     */
    List<String> saveModelsList(String key, List<String> modelsList);
}
