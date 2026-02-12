package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.RequestCancelRegistry;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.CommentService;
import com.nju.comment.backend.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final LLMService llmService;
    private final CacheService cacheService;
    private final RequestCancelRegistry requestCancelRegistry;
    private final ThreadPoolTaskExecutor llmTaskExecutor;

    @Override
    public CompletableFuture<CommentResponse> generateComment(CommentRequest request) {
        // 获取或生成请求ID
        String requestId = request.getClientRequestId() != null && !request.getClientRequestId().isBlank()
                ? request.getClientRequestId()
                : UUID.randomUUID().toString();

        // 使用专用线程池执行，并将真正执行的 Future 注册到取消管理器，确保 cancel(true) 能中断线程
        CompletableFuture<CommentResponse> future = CompletableFuture.supplyAsync(() -> {
            // 注册当前执行线程，使得cancel时能直接中断阻塞I/O（如LLM网络请求）
            requestCancelRegistry.registerThread(requestId, Thread.currentThread());
            Instant startTime = Instant.now();
            try {
                log.info("开始处理注释生成请求, requestId={}, thread={}", requestId, Thread.currentThread().getName());

                // 检查请求是否已被取消
                if (requestCancelRegistry.isCancelled(requestId)) {
                    log.warn("注释生成请求已被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                // 检查缓存
                String key = generateCommentCacheKey(request);
                CommentResponse cachedResponse = cacheService.getComment(key);
                if (cachedResponse != null) {
                    log.info("注释生成请求命中缓存, requestId={}", requestId);
                    cachedResponse.setRequestId(requestId);
                    cachedResponse.setProcessingTimeMs(Duration.between(startTime, Instant.now()).toMillis());
                    return cachedResponse;
                }

                // 再次检查请求是否已被取消
                if (requestCancelRegistry.isCancelled(requestId)) {
                    log.warn("注释生成请求在缓存检查后被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                // 调用 LLM 服务生成注释（支持中断线程取消）
                String generatedComment;
                try {
                    generatedComment = llmService.generateComment(request);
                } catch (ServiceException e) {
                    // 如果是中断导致的异常，按取消处理
                    if (ErrorCode.LLM_INTERRUPTED.getCode() == e.getErrorCode().getCode() ||
                            requestCancelRegistry.isCancelled(requestId) ||
                            Thread.currentThread().isInterrupted()) {
                        log.info("注释生成在LLM调用阶段被中断, requestId={}", requestId);
                        return CommentResponse.cancelled(requestId);
                    }
                    // 其他ServiceException直接抛出
                    throw e;
                }

                // 再次检查是否在生成过程中被取消
                if (requestCancelRegistry.isCancelled(requestId)) {
                    log.info("注释生成在后处理阶段被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                String processedComment = postProcessComment(generatedComment);

                // 构建响应并缓存结果
                CommentResponse response = CommentResponse.success(processedComment)
                        .withRequestId(requestId)
                        .withModelUsed(request.getModelName())
                        .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

                cacheService.saveComment(key, response);

                if (requestCancelRegistry.isCancelled(requestId)) {
                    log.info("注释生成在缓存保存后被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
                return response;
            } catch (Exception e) {
                log.error("注释生成请求处理失败, requestId={}", requestId, e);
                throw new ServiceException(ErrorCode.COMMENT_SERVICE_ERROR, e);
            }
        }, llmTaskExecutor);

        requestCancelRegistry.register(requestId, future);
        return future;
    }

    private String postProcessComment(String generatedComment) {
        //TODO
        return generatedComment;
    }

    private String generateCommentCacheKey(CommentRequest request) {
        return String.format("%s:%s:%s:%s:%s",
                request.getOldMethod(),
                request.getOldComment(),
                request.getNewMethod(),
                request.getModelName(),
                request.isRag()
        );
    }
}
