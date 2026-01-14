package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.CommentBaseService;
import com.nju.comment.backend.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentBaseServiceImpl implements CommentBaseService {

    private final LLMService llmService;

    private final CacheService cacheService;

    @Override
    @Async("llmTaskExecutor")
    public CompletableFuture<CommentResponse> generateComment(CommentRequest request) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("开始处理注释生成请求, requestId={}", requestId);

        try {
            String key = generateCommentCacheKey(request);
            CommentResponse cachedResponse = cacheService.getComment(key);
            if (cachedResponse != null) {
                log.info("注释生成请求命中缓存, requestId={}", requestId);
                cachedResponse.setRequestId(requestId);
                cachedResponse.setProcessingTimeMs(Duration.between(startTime, Instant.now()).toMillis());
                return CompletableFuture.completedFuture(cachedResponse);
            }

            String generatedComment = llmService.generateComment(request);
            String processedComment = postProcessComment(generatedComment, request);

            CommentResponse response = CommentResponse.success(processedComment)
                    .withRequestId(requestId)
                    .withModelUsed(request.getModelName())
                    .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

            cacheService.saveComment(key, response);

            log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("注释生成请求处理失败, requestId={}", requestId, e);
            throw new ServiceException(ErrorCode.COMMENT_SERVICE_ERROR, e);
        }
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
