package com.nju.comment.backend.component.embedding;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.VectorStoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EmbeddingModel 包装器：
 * 1) 在调用前执行 RPM/TPM 限流；
 * 2) 命中 429/限流错误时指数退避重试。
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final SiliconFlowEmbeddingRateLimiter rateLimiter;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    @Override
    public @NonNull EmbeddingResponse call(@NonNull EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        int attempts = Math.max(1, maxRetries + 1);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                rateLimiter.acquire(inputs);
                return delegate.call(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VectorStoreException(ErrorCode.VECTOR_STORE_INTERRUPTED, "Embedding 请求被中断", e);
            } catch (Exception e) {
                if (!isRateLimitError(e)) {
                    throw e;
                }

                if (attempt >= attempts) {
                    throw new VectorStoreException(
                            ErrorCode.RATE_LIMIT_EXCEEDED,
                            "Embedding 请求触发限流，已超过最大重试次数",
                            e
                    );
                }

                long delayMs = computeBackoffDelay(attempt);
                log.warn("Embedding 请求触发限流，准备重试: attempt={}/{}, delay={}ms",
                        attempt, attempts, delayMs);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new VectorStoreException(ErrorCode.VECTOR_STORE_INTERRUPTED, "Embedding 重试等待被中断", ie);
                }
            }
        }

        throw new VectorStoreException(ErrorCode.RATE_LIMIT_EXCEEDED, "Embedding 请求触发限流");
    }

    @Override
    public float @NonNull [] embed(@NonNull Document document) {
        String content = document.getFormattedContent(MetadataMode.NONE);
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(content), null));
        return response.getResult().getOutput();
    }

    private long computeBackoffDelay(int attempt) {
        long safeInitial = Math.max(200L, initialBackoffMs);
        long safeMax = Math.max(safeInitial, maxBackoffMs);
        long exponential = safeInitial * (1L << Math.max(0, attempt - 1));
        long bounded = Math.min(safeMax, exponential);
        long jitter = ThreadLocalRandom.current().nextLong(50L, 250L);
        return bounded + jitter;
    }

    private boolean isRateLimitError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("429")
                        || normalized.contains("too many requests")
                        || normalized.contains("rate limit")
                        || normalized.contains("rate_limit")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
