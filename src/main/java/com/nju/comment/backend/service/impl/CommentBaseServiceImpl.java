package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.RequestCancelRegistry;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.CommentBaseService;
import com.nju.comment.backend.service.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class CommentBaseServiceImpl implements CommentBaseService {

    private final LLMService llmService;
    private final CacheService cacheService;
    private final RequestCancelRegistry requestCancelRegistry;
    private final Executor llmTaskExecutor;

    public CommentBaseServiceImpl(LLMService llmService, CacheService cacheService,
                                  RequestCancelRegistry requestCancelRegistry,
                                  @Qualifier("llmTaskExecutor") Executor llmTaskExecutor) {
        this.llmService = llmService;
        this.cacheService = cacheService;
        this.requestCancelRegistry = requestCancelRegistry;
        this.llmTaskExecutor = llmTaskExecutor;
    }

    @Override
    public CompletableFuture<CommentResponse> generateComment(CommentRequest request) {
        String requestId = request.getClientRequestId() != null && !request.getClientRequestId().isBlank()
                ? request.getClientRequestId()
                : UUID.randomUUID().toString();

        CompletableFuture<CommentResponse> result = new CompletableFuture<>();
        requestCancelRegistry.register(requestId, result);

        llmTaskExecutor.execute(() -> {
            Instant startTime = Instant.now();
            try {
                log.info("开始处理注释生成请求, requestId={}", requestId);

                if (requestCancelRegistry.isCancelled(requestId)) {
                    log.warn("注释生成请求已被取消, requestId={}", requestId);
                    result.complete(CommentResponse.cancelled(requestId));
                    return;
                }

                String key = generateCommentCacheKey(request);
                CommentResponse cachedResponse = cacheService.getComment(key);
                if (cachedResponse != null) {
                    log.info("注释生成请求命中缓存, requestId={}", requestId);
                    cachedResponse.setRequestId(requestId);
                    cachedResponse.setProcessingTimeMs(Duration.between(startTime, Instant.now()).toMillis());
                    result.complete(cachedResponse);
                    return;
                }

                if (requestCancelRegistry.isCancelled(requestId)) {
                    result.complete(CommentResponse.cancelled(requestId));
                    return;
                }

                String generatedComment = llmService.generateComment(request);
                String processedComment = postProcessComment(generatedComment, request);

                CommentResponse response = CommentResponse.success(processedComment)
                        .withRequestId(requestId)
                        .withModelUsed(request.getModelName())
                        .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

                cacheService.saveComment(key, response);

                log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
                result.complete(response);
            } catch (Exception e) {
                log.error("注释生成请求处理失败, requestId={}", requestId, e);
                result.completeExceptionally(new ServiceException(ErrorCode.COMMENT_SERVICE_ERROR, e));
            } finally {
                requestCancelRegistry.unregister(requestId);
            }
        });

        return result;
    }

    private String postProcessComment(String generatedComment, CommentRequest request) {
        //TODO
        return generatedComment;
    }

    private String generateCommentCacheKey(CommentRequest request) {
        return String.format("%s:%s:%s:%s",
                request.getOldMethod(),
                request.getOldComment(),
                request.getNewMethod(),
                request.getModelName()
        );
    }
}
