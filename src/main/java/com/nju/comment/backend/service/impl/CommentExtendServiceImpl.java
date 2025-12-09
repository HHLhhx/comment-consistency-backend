package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CommentBaseService;
import com.nju.comment.backend.service.CommentExtendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentExtendServiceImpl implements CommentExtendService {

    @Value("${app.comment.max-batch-num:10}")
    private int MAX_BATCH_SIZE;

    private final CommentBaseService commentService;

    @Override
    @Async("llmTaskExecutor")
    public CompletableFuture<List<CommentResponse>> batchGenerateComments(List<CommentRequest> request) {
        if (request == null || request.isEmpty()) {
            log.info("批量注释生成请求为空，直接返回空列表");
            return CompletableFuture.completedFuture(List.of());
        }

        if (request.size() > MAX_BATCH_SIZE) {
            log.warn("批量注释生成请求数量超过最大限制: {}, 请求数量={}", MAX_BATCH_SIZE, request.size());
            throw new IllegalArgumentException("批量注释生成请求超过最大限制: " + MAX_BATCH_SIZE);
        }

        Instant startTime = Instant.now();

        log.info("开始处理批量注释生成请求, 请求数量={}", request.size());

        List<CompletableFuture<CommentResponse>> future = request.stream()
                .map(commentService::generateComment)
                .toList();

        CompletableFuture<List<CommentResponse>> result = CompletableFuture.allOf(future.toArray(new CompletableFuture[0]))
                .thenApply(v -> future.stream()
                        .map(CompletableFuture::join)
                        .toList())
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("批量注释生成请求处理失败, 错误信息={}", ex.getMessage());
                    } else {
                        log.info("批量注释生成请求处理完成，总耗时={}ms", Duration.between(startTime, Instant.now()).toMillis());
                    }
                });

        return result;
    }
}
