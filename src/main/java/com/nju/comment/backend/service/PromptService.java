package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;

public interface PromptService {

    /**
     * 构建提示词
     */
    String buildPrompt(CommentRequest request);
}
