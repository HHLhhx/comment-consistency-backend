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

    /**
     * 获取所使用的聊天模型名称
     */
    String getChatModelName();
}
