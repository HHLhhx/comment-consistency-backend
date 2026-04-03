package com.nju.comment.backend.component.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * SiliconFlow Embedding 速率限制器（滑动窗口）。
 * 同时约束 RPM（请求数/分钟）和 TPM（Token/分钟）。
 */
@Slf4j
@Component
public class SiliconFlowEmbeddingRateLimiter {

    private static final long DEFAULT_WINDOW_MS = 60_000L;

    private final Object monitor = new Object();
    private final Deque<WindowRecord> records = new ArrayDeque<>();

    private long rollingTokens = 0;

    @Value("${app.ai.siliconflow.embedding.rate-limit.rpm:2000}")
    private int rpmLimit;

    @Value("${app.ai.siliconflow.embedding.rate-limit.tpm:500000}")
    private int tpmLimit;

    @Value("${app.ai.siliconflow.embedding.rate-limit.window-ms:60000}")
    private long windowMs;

    /**
     * 根据输入文本的近似 token 数申请额度，必要时阻塞等待。
     */
    public void acquire(List<String> inputs) throws InterruptedException {
        int estimatedTokens = sanitizeEstimatedTokens(estimateTokens(inputs));

        while (true) {
            long waitMs;
            long now = System.currentTimeMillis();
            synchronized (monitor) {
                evictExpired(now);
                waitMs = computeWaitMs(now, estimatedTokens);
                if (waitMs <= 0) {
                    records.addLast(new WindowRecord(now, estimatedTokens));
                    rollingTokens += estimatedTokens;
                    return;
                }
            }

            log.debug("Embedding 限流等待: wait={}ms, estimatedTokens={}, rpmLimit={}, tpmLimit={}",
                    waitMs, estimatedTokens, rpmLimit, tpmLimit);
            Thread.sleep(waitMs);
        }
    }

    /**
     * 对文本进行保守 token 估算：按 Unicode code point 近似。
     */
    int estimateTokens(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return 1;
        }

        long total = 0;
        for (String input : inputs) {
            if (input == null || input.isBlank()) {
                continue;
            }
            total += input.codePointCount(0, input.length());
        }

        return (int) Math.max(1L, total);
    }

    private int sanitizeEstimatedTokens(int estimatedTokens) {
        int safeTpmLimit = Math.max(1, tpmLimit);
        int safeEstimatedTokens = Math.max(1, estimatedTokens);
        if (safeEstimatedTokens > safeTpmLimit) {
            log.warn("单次 embedding 估算 token({}) 超过 TPM 限制({})，已按 TPM 上限参与限流，请考虑减小批次大小",
                    safeEstimatedTokens, safeTpmLimit);
            return safeTpmLimit;
        }
        return safeEstimatedTokens;
    }

    private long computeWaitMs(long now, int estimatedTokens) {
        int safeRpmLimit = Math.max(1, rpmLimit);
        long safeWindowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;

        long rpmWait = 0;
        if (records.size() >= safeRpmLimit) {
            WindowRecord first = records.peekFirst();
            if (first != null) {
                rpmWait = first.timestamp + safeWindowMs - now;
            }
        }

        long tpmWait = 0;
        long overflow = rollingTokens + estimatedTokens - Math.max(1, tpmLimit);
        if (overflow > 0) {
            long released = 0;
            for (WindowRecord record : records) {
                released += record.tokens;
                if (released >= overflow) {
                    tpmWait = record.timestamp + safeWindowMs - now;
                    break;
                }
            }
            if (tpmWait <= 0) {
                WindowRecord first = records.peekFirst();
                if (first != null) {
                    tpmWait = first.timestamp + safeWindowMs - now;
                }
            }
        }

        long waitMs = Math.max(rpmWait, tpmWait);
        return Math.max(0L, waitMs);
    }

    private void evictExpired(long now) {
        long safeWindowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;

        while (!records.isEmpty()) {
            WindowRecord first = records.peekFirst();
            if (first == null || now - first.timestamp < safeWindowMs) {
                break;
            }
            records.removeFirst();
            rollingTokens -= first.tokens;
            if (rollingTokens < 0) {
                rollingTokens = 0;
            }
        }
    }

    private record WindowRecord(long timestamp, int tokens) {
    }
}
