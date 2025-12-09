package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CommentBaseService;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.PromptService;
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

    private final PromptService promptService;

    @Override
    @Async("llmTaskExecutor")
    public CompletableFuture<CommentResponse> generateComment(CommentRequest request) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("开始处理注释生成请求, requestId={}", requestId);

        try {
            //TODO: cache

            String prompt = promptService.buildPrompt(request);
            String generatedComment = llmService.generateComment(prompt);

            //TODO: process generated comment

            CommentResponse response = CommentResponse.success(generatedComment)
                    .withRequestId(requestId)
                    .withModelUsed(llmService.getChatModelName())
                    .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

            //TODO: cache save

            log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("注释生成请求处理失败, requestId={}, 错误信息={}", requestId, e.getMessage());

            CommentResponse errorResponse = CommentResponse.error(e.getMessage())
                    .withRequestId(requestId)
                    .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

            return CompletableFuture.completedFuture(errorResponse);
        }
    }

    @Override
    public boolean isServiceHealthy() {
        return llmService.isServiceHealthy();
    }
}
