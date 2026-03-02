package com.nju.comment.backend.service;

import com.nju.comment.backend.dto.request.CancelRequest;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import jakarta.validation.Valid;

import java.util.concurrent.CompletableFuture;

public interface CommentService {

    /**
     * 生成单个注释
     */
    CompletableFuture<CommentResponse> generateComment(String username, CommentRequest request);

    /**
     * 取消正在处理的注释生成请求
     */
    void cancel(CancelRequest request);
}
