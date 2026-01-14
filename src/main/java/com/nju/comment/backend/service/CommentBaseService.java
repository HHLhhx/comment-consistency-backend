package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;

import java.util.concurrent.CompletableFuture;

public interface CommentBaseService {

    /**
     * 生成单个注释
     */
    CompletableFuture<CommentResponse> generateComment(CommentRequest request);
}
