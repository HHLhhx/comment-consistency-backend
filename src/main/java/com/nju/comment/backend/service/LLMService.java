package com.nju.comment.backend.service;

public interface LLMService {

    /**
     * 生成注释文本
     */
    String generateComment(String prompt);

    /**
     * 检查服务健康状态
     */
    boolean isServiceHealthy();
}
