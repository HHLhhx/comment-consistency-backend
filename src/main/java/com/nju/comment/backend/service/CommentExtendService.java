package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CommentExtendService {

    /**
     * 批量生成注释
     */
    CompletableFuture<List<CommentResponse>> batchGenerateComments(List<CommentRequest> request);
}
