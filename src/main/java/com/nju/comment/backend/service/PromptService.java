package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;

public interface PromptService {

    /**
     * 构建提示词
     */
    String buildUserPrompt(CommentRequest request);

    /**
     * 获取系统提示词
     */
    String getSystemPrompt(CommentRequest request);
}
