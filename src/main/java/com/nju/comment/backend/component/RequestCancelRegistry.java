package com.nju.comment.backend.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理注释生成请求的取消状态与异步任务句柄，供客户端调用 /cancel 时取消未完成任务。
 */
@Component
@Slf4j
public class RequestCancelRegistry {

    private final Map<String, CompletableFuture<?>> inFlightFutures = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cancelledIds = new ConcurrentHashMap<>();

    public void register(String requestId, CompletableFuture<?> future) {
        if (requestId == null || requestId.isBlank()) return;
        inFlightFutures.put(requestId, future);
        future.whenComplete((r, ex) -> unregister(requestId));
    }

    public void unregister(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        inFlightFutures.remove(requestId);
        cancelledIds.remove(requestId);
    }

    /**
     * 标记为已取消，并尝试取消正在执行的 Future。
     * 若该 requestId 对应任务尚未开始 LLM 调用，会在执行前通过 isCancelled 被跳过。
     */
    public void cancel(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        cancelledIds.put(requestId, Boolean.TRUE);
        CompletableFuture<?> f = inFlightFutures.get(requestId);
        if (f != null) {
            f.cancel(true);
            log.info("已取消请求 requestId={}", requestId);
        }
    }

    public boolean isCancelled(String requestId) {
        return requestId != null && Boolean.TRUE.equals(cancelledIds.get(requestId));
    }
}
