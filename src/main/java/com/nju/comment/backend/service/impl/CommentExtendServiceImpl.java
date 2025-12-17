package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentExtendServiceImpl implements CommentExtendService {

    @Value("${app.comment.max-batch-num:10}")
    private int MAX_BATCH_SIZE;

    @Value("${app.comment.llm-timeout-seconds:20}")
    private long LLM_TIMEOUT_SECONDS;

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
            throw new ServiceException(ErrorCode.COMMENT_REQUEST_NUM_EXCEEDED, "批量注释生成请求数量超过最大限制: " + MAX_BATCH_SIZE);
        }

        Instant startTime = Instant.now();

        log.info("开始处理批量注释生成请求, 请求数量={}", request.size());

        List<CompletableFuture<CommentResponse>> future = request.stream()
                .map(commentService::generateComment)
                .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(future.toArray(new CompletableFuture[0]));

        try {
            all.get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<CommentResponse> results = future.stream()
                    .map(CompletableFuture::join)
                    .toList();

            log.info("批量注释生成请求处理完成，总耗时={}ms", Duration.between(startTime, Instant.now()).toMillis());
            return CompletableFuture.completedFuture(results);
        } catch (TimeoutException te) {
            log.error("批量注释生成超时（{}ms），尝试取消子任务", LLM_TIMEOUT_SECONDS);
            future.forEach(f -> f.cancel(true));
            throw new ServiceException(ErrorCode.LLM_TIMEOUT, te);
        }  catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("批量注释生成被中断", ie);
            future.forEach(f -> f.cancel(true));
            throw new ServiceException(ErrorCode.LLM_INTERRUPTED, ie);
        } catch (ExecutionException ee) {
            log.error("批量注释生成执行失败", ee);
            future.forEach(f -> f.cancel(true));
            throw new ServiceException(ErrorCode.LLM_EXECUTION_ERROR, ee);
        } catch (Exception ex) {
            log.error("批量注释生成请求处理失败, 错误信息={}", ex.getMessage());
            future.forEach(f -> f.cancel(true));
            throw new ServiceException(ErrorCode.LLM_SERVICE_ERROR, ex);
        }
    }
}
