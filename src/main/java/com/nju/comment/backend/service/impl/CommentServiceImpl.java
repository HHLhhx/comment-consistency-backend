package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.component.RequestCancelRegistry;
import com.nju.comment.backend.dto.request.CommentReqTag;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.service.CacheService;
import com.nju.comment.backend.service.CommentService;
import com.nju.comment.backend.service.LLMService;
import com.nju.comment.backend.service.impl.UserApiKeyService;
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
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final LLMService llmService;
    private final CacheService cacheService;
    private final UserApiKeyService userApiKeyService;
    private final RequestCancelRegistry requestCancelRegistry;
    private final ThreadPoolTaskExecutor llmTaskExecutor;
    private final ScheduledExecutorService llmTimeoutScheduler;

    @Value("${app.ai.llm.timeout-ms:30000}")
    private long defaultTimeoutMs;

    @Override
    public CompletableFuture<CommentResponse> generateComment(CommentRequest request) {
        // 获取请求ID
        String requestId = request.getRequestId();

        long timeoutMs = resolveTimeoutMs(request);

        // 在请求线程中捕获用户名和 API Key，避免异步线程池中 SecurityContext 不可用
        String currentUsername = CacheServiceImpl.getCurrentUsername();
        String userApiKey = userApiKeyService.getDecryptedApiKey(currentUsername);
        if (userApiKey == null || userApiKey.isBlank()) {
            throw new ServiceException(ErrorCode.AUTH_API_KEY_NOT_SET);
        }

        // 使用专用线程池执行，并将真正执行的 Future 注册到取消管理器，确保 cancel(true) 能中断线程
        CompletableFuture<CommentResponse> future = CompletableFuture.supplyAsync(() -> {
            // 在异步线程中设置 API Key 上下文
            UserApiContext.setApiKey(userApiKey);
            // 注册当前执行线程，使得cancel时能直接中断阻塞I/O（如LLM网络请求）
            requestCancelRegistry.registerThread(requestId, Thread.currentThread());
            Instant startTime = Instant.now();
            try {
                log.info("开始处理注释生成请求, requestId={}, thread={}", requestId, Thread.currentThread().getName());

                // 检查请求是否已被取消
                if (isStopped(requestId)) {
                    log.warn("注释生成请求已被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                // 检查缓存
                String key = generateCommentCacheKey(request, currentUsername);
                String cachedComment = cacheService.getComment(key);
                if (cachedComment != null) {
                    log.info("注释生成请求命中缓存, requestId={}", requestId);
                    // 仅复用缓存中的生成结果，重建本次请求的上下文字段
                    return CommentResponse.success(cachedComment)
                            .withRequestId(requestId)
                            .withModelUsed(request.getModelName())
                            .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());
                }

                // 再次检查请求是否已被取消
                if (isStopped(requestId)) {
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
                            isStopped(requestId) ||
                            Thread.currentThread().isInterrupted()) {
                        log.info("注释生成在LLM调用阶段被中断, requestId={}", requestId);
                        return CommentResponse.cancelled(requestId);
                    }
                    // 其他ServiceException直接抛出
                    throw e;
                }

                // 再次检查是否在生成过程中被取消
                if (isStopped(requestId)) {
                    log.info("注释生成在后处理阶段被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                String processedComment = postProcessComment(generatedComment);

                // 将结果保存到缓存
                cacheService.saveComment(key, processedComment);
                if (isStopped(requestId)) {
                    log.info("注释生成在缓存保存后被取消, requestId={}", requestId);
                    return CommentResponse.cancelled(requestId);
                }

                // 构建响应
                CommentResponse response = CommentResponse.success(processedComment)
                        .withRequestId(requestId)
                        .withModelUsed(request.getModelName())
                        .withProcessingTime(Duration.between(startTime, Instant.now()).toMillis());

                log.info("注释生成请求处理完成, requestId={}, 耗时={}ms", requestId, response.getProcessingTimeMs());
                return response;
            } catch (Exception e) {
                log.error("注释生成请求处理失败, requestId={}", requestId, e);
                throw new ServiceException(ErrorCode.COMMENT_SERVICE_ERROR, e);
            } finally {
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
            future.completeExceptionally(new TimeoutException("LLM调用超时"));
        }, timeoutMs, TimeUnit.MILLISECONDS);

        future.whenComplete((r, ex) -> timeoutFuture.cancel(false));

        requestCancelRegistry.register(requestId, future);
        return future;
    }

    private String postProcessComment(String generatedComment) {
        //TODO
        return generatedComment;
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
     * 保证 key 简短且可解释：一眼可知是哪个用户、哪个模型、是否 RAG，
     * 相同输入内容产生相同 hash 以命中缓存。
     *
     * @param request  注释生成请求
     * @param username 调用者用户名（在请求线程中提前捕获）
     */
    private String generateCommentCacheKey(CommentRequest request, String username) {
        String model = request.getModelName() != null
                ? request.getModelName().replace(':', '-')   // qwen3-coder:480b → qwen3-coder-480b，避免 : 被 Redis 视为层级分隔
                : "default";
        String rag = CommentReqTag.GENERATE.equals(request.getTag()) ? "generate" :
                (CommentReqTag.UPDATE_WITH_RAG.equals(request.getTag()) ? "rag" : "update");
        String contentHash = shortHash(
                nullSafe(request.getOldMethod()),
                nullSafe(request.getOldComment()),
                nullSafe(request.getNewMethod())
        );
        // 最终 Redis key 示例: cc:comment:john:llama3:r:a1b2c3d4e5f6g7h8
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
            return HexFormat.of().formatHex(digest, 0, 8); // 8字节 = 16个十六进制字符
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
