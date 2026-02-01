package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;

public interface PromptService {

    /**
     * 构建用户提示语
     */
    String buildUserPrompt(CommentRequest request);

    /**
     * 构建系统提示语
     */
    String getSystemPrompt(CommentRequest request);
}
