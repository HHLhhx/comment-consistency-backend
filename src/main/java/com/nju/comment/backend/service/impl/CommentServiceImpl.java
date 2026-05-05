package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.RequestCancelRegistry;
import com.nju.comment.backend.dto.request.CancelRequest;
import com.nju.comment.backend.dto.request.CommentReqTag;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.CommentService;
import com.nju.comment.backend.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?s)```(?:\\w+)?\\s*(.*?)\\s*```");

    private final LLMService llmService;
    private final CacheService cacheService;
    private final UserApiKeyService userApiKeyService;
    private final RequestCancelRegistry requestCancelRegistry;
    private final ThreadPoolTaskExecutor llmTaskExecutor;
    private final ScheduledExecutorService llmTimeoutScheduler;
    private final MetricsService metricsService;

    @Value("${app.ai.llm.timeout-ms:30000}")
    private long defaultTimeoutMs;

    @Override
    public CompletableFuture<CommentResponse> generateComment(String username, CommentRequest request) {
        // 获取请求ID
        String requestId = request.getRequestId();

        long timeoutMs = resolveTimeoutMs(request);

        // 在请求线程中捕获用户名和 API Key/Base URL，避免异步线程池中 SecurityContext 不可用
        String userApiKey = userApiKeyService.getDecryptedApiKey(username);
        if (userApiKey == null || userApiKey.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
        }
        String userBaseUrl = userApiKeyService.getBaseUrl(username);

        // 使用专用线程池执行，并将真正执行的 Future 注册到取消管理器，确保 cancel(true) 能中断线程
        CompletableFuture<CommentResponse> future = CompletableFuture.supplyAsync(() -> {
            // 在异步线程中设置 API Key + Base URL 上下文
            UserApiContext.setCredential(userApiKey, userBaseUrl);
            // 注册当前执行线程，使得cancel时能直接中断阻塞I/O（如LLM网络请求）
            requestCancelRegistry.registerThread(requestId, Thread.currentThread());
            Instant startTime = Instant.now();
            try {
                log.info("开始处理注释生成请求, requestId={}, thread={}", requestId, Thread.currentThread().getName());

                // 检查请求是否已被取消
                if (isStopped(requestId)) {
                    log.warn("注释生成请求已被取消, requestId={}", requestId);
                    metricsService.recordCommentGeneration("cancelled",
                            request.getModelName(),
                            Duration.between(startTime, Instant.now()));
                    return CommentResponse.cancelled(requestId);
                }

                // 检查缓存
                String key = generateCommentCacheKey(request, username);
                String cachedComment = cacheService.getComment(key);
                if (cachedComment != null) {
                    log.info("注释生成请求命中缓存, requestId={}", requestId);
                    metricsService.recordCommentCacheHit();
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    metricsService.recordCommentGeneration("cached",
                            request.getModelName(), elapsed);
                    // 重建本次请求的上下文字段
                    return CommentResponse.success(cachedComment)
                            .withRequestId(requestId)
                            .withModelUsed(request.getModelName())
                            .withProcessingTime(elapsed.toMillis());
                }

                // 再次检查请求是否已被取消
                if (isStopped(requestId)) {
                    log.warn("注释生成请求在缓存检查后被取消, requestId={}", requestId);
                    metricsService.recordCommentGeneration("cancelled",
                            request.getModelName(),
                            Duration.between(startTime, Instant.now()));
                    return CommentResponse.cancelled(requestId);
                }

                // 调用 LLM 服务生成注释
                String generatedComment;
                try {
                    generatedComment = llmService.generateComment(request);
                } catch (ServiceException e) {
                    // 如果是中断导致的异常，按取消处理
                    if (ErrorCode.LLM_INTERRUPTED.getCode() == e.getErrorCode().getCode() ||
                            isStopped(requestId) ||
                            Thread.currentThread().isInterrupted()) {
                        log.info("注释生成在LLM调用阶段被中断, requestId={}", requestId);
                        metricsService.recordCommentGeneration("cancelled",
                                request.getModelName(),
                                Duration.between(startTime, Instant.now()));
                        return CommentResponse.cancelled(requestId);
                    }
                    // 其他ServiceException直接抛出
                    throw e;
                }

                // 再次检查是否在生成过程中被取消
                if (isStopped(requestId)) {
                    log.info("注释生成在后处理阶段被取消, requestId={}", requestId);
                    metricsService.recordCommentGeneration("cancelled",
                            request.getModelName(),
                            Duration.between(startTime, Instant.now()));
                    return CommentResponse.cancelled(requestId);
                }

                // 对生成结果进行后处理
                String processedComment = postProcessComment(generatedComment);

                // 将结果保存到缓存
                cacheService.saveComment(key, processedComment);
                if (isStopped(requestId)) {
                    log.info("注释生成在缓存保存后被取消, requestId={}", requestId);
                    metricsService.recordCommentGeneration("cancelled",
                            request.getModelName(),
                            Duration.between(startTime, Instant.now()));
                    return CommentResponse.cancelled(requestId);
                }

                // 构建响应
                CommentResponse response = CommentResponse.success(processedComment)
                        .withRequestId(requestId)
                        .withModelUsed(request.getModelName())
                        .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

                log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
                metricsService.recordCommentGeneration("success",
                        request.getModelName(),
                        Duration.ofMillis(response.getProcessingTimeMs()));
                return response;
            } catch (ServiceException e) {
                log.warn("注释生成请求处理失败, requestId={}", requestId);
                String outcome = ErrorCode.LLM_TIMEOUT.equals(e.getErrorCode()) ? "timeout" : "failure";
                metricsService.recordCommentGeneration(outcome,
                        request.getModelName(),
                        Duration.between(startTime, Instant.now()));
                throw e;
            } catch (Exception e) {
                log.error("注释生成请求处理失败, requestId={}", requestId);
                metricsService.recordCommentGeneration("failure",
                        request.getModelName(),
                        Duration.between(startTime, Instant.now()));
                throw new ServiceException(ErrorCode.COMMENT_SERVICE_ERROR, e);
            } finally {
                // 清理上下文和线程注册，避免内存泄漏
                UserApiContext.clear();
            }
        }, llmTaskExecutor);

        // 设置超时任务，超时后如果 Future 还未完成，则标记请求为超时并完成 Future 异常
        ScheduledFuture<?> timeoutFuture = llmTimeoutScheduler.schedule(() -> {
            if (future.isDone()) {
                return;
            }
            log.warn("注释生成请求超时，requestId={}，timeoutMs={}", requestId, timeoutMs);
            requestCancelRegistry.timeout(requestId);
            future.completeExceptionally(new LLMException(ErrorCode.LLM_TIMEOUT));
        }, timeoutMs, TimeUnit.MILLISECONDS);

        // 无论正常完成还是异常完成，都取消超时任务，避免不必要的调度执行
        future.whenComplete((r, ex) -> timeoutFuture.cancel(false));

        // 将 Future 注册到取消管理器，以便在取消时能正确中断执行线程
        requestCancelRegistry.register(requestId, future);
        return future;
    }

    @Override
    public void cancel(CancelRequest request) {
        requestCancelRegistry.cancel(request.getRequestId());
    }

    private String postProcessComment(String generatedComment) {
        String normalized = generatedComment == null ? "" : generatedComment.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return "/**\n * TODO\n */";
        }

        // LLM 常会把答案包在 markdown 代码块中，先提取真实内容。
        Matcher fenceMatcher = CODE_FENCE_PATTERN.matcher(normalized);
        if (fenceMatcher.find()) {
            normalized = fenceMatcher.group(1).trim();
        }

        String extracted = extractCommentBodyFromBlock(normalized);
        if (extracted == null) {
            extracted = normalized;
        }
        return toJavadocBlock(extracted);
    }

    private String extractCommentBodyFromBlock(String text) {
        int javadocStart = text.indexOf("/**");
        if (javadocStart >= 0) {
            int end = text.indexOf("*/", javadocStart + 3);
            if (end > javadocStart) {
                return text.substring(javadocStart + 3, end);
            }
        }

        int blockStart = text.indexOf("/*");
        if (blockStart >= 0) {
            int end = text.indexOf("*/", blockStart + 2);
            if (end > blockStart) {
                return text.substring(blockStart + 2, end);
            }
        }

        return null;
    }

    private String toJavadocBlock(String rawBody) {
        String[] lines = rawBody.replace("\r\n", "\n").split("\\n", -1);
        List<String> cleanedLines = new ArrayList<>(lines.length);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            cleanedLines.add(trimmed);
        }

        while (!cleanedLines.isEmpty() && cleanedLines.get(0).isEmpty()) {
            cleanedLines.remove(0);
        }
        while (!cleanedLines.isEmpty() && cleanedLines.get(cleanedLines.size() - 1).isEmpty()) {
            cleanedLines.remove(cleanedLines.size() - 1);
        }
        if (cleanedLines.isEmpty()) {
            cleanedLines.add("TODO");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        for (String line : cleanedLines) {
            if (line.isEmpty()) {
                sb.append(" *\n");
            } else {
                sb.append(" * ").append(line).append("\n");
            }
        }
        sb.append(" */");
        return sb.toString();
    }

    private boolean isStopped(String requestId) {
        return requestCancelRegistry.isCancelled(requestId)
                || requestCancelRegistry.isTimedOut(requestId)
                || Thread.currentThread().isInterrupted();
    }

    private long resolveTimeoutMs(CommentRequest request) {
        if (request.getTimeoutMs() != null && request.getTimeoutMs() > 0) {
            return request.getTimeoutMs();
        }
        return defaultTimeoutMs;
    }

    /**
     * 生成注释缓存 key，格式: {username}:{model}:{rag}:{contentHash}
     * <p>
     * 其中 contentHash 为 oldMethod + oldComment + newMethod 的 SHA-256 前16位十六进制，
     *
     * @param request  注释生成请求
     * @param username 调用者用户名（在请求线程中提前捕获）
     */
    private String generateCommentCacheKey(CommentRequest request, String username) {
        String model = request.getModelName() != null
                ? request.getModelName().replace(':', '-')
                : "default";
        String rag = CommentReqTag.GENERATE.equals(request.getTag()) ? "generate" :
                (CommentReqTag.UPDATE_WITH_RAG.equals(request.getTag()) ? "rag" : "update");
        String contentHash = shortHash(
                nullSafe(request.getOldMethod()),
                nullSafe(request.getOldComment()),
                nullSafe(request.getNewMethod())
        );
        return String.format("%s:%s:%s:%s", username, model, rag, contentHash);
    }

    /**
     * 对多段内容计算 SHA-256，取前16位十六进制作为短摘要
     */
    private String shortHash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                md.update(part.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\0'); // 分隔符，避免 "ab"+"c" 与 "a"+"bc" 碰撞
            }
            byte[] digest = md.digest();
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
