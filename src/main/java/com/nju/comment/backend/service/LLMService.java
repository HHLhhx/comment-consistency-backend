package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;

import java.util.List;

public interface LLMService {

    /**
     * 生成注释文本
     */
    String generateComment(CommentRequest request);

    /**
     * 获取可用模型列表
     */
    List<String> getAvailableModels();
}
