package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CommentService {

    /**
     * 生成单个注释
     */
    CompletableFuture<CommentResponse> generateComment(CommentRequest request);

    /**
     * 批量生成注释
     */
    CompletableFuture<List<CommentResponse>> batchGenerateComments(List<CommentRequest> request);

    /**
     * 生成多个备选注释
     */
    CompletableFuture<CommentResponse> generateWithAlternatives(CommentRequest request, int count);

    /**
     * 异步生成注释（返回Future）
     */
    CompletableFuture<String> generateCommentAsync(CommentRequest request);

    /**
     * 检查服务健康状态
     */
    boolean isServiceHealthy();
}
