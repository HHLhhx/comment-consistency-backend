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
    private final Map<String, Thread> executingThreads = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cancelledIds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> timedOutIds = new ConcurrentHashMap<>();

    /**
     * 注册正在执行的 Future 任务，供取消时调用。
     * 同时注册一个回调，在任务完成后自动清理注册信息，避免内存泄漏。
     */
    public void register(String requestId, CompletableFuture<?> future) {
        if (requestId == null || requestId.isBlank()) return;
        inFlightFutures.put(requestId, future);
        future.whenComplete((r, ex) -> unregister(requestId));
    }

    /**
     * 注册执行任务的线程，用于取消时直接中断线程
     */
    public void registerThread(String requestId, Thread thread) {
        if (requestId == null || requestId.isBlank()) return;
        executingThreads.put(requestId, thread);
    }

    /**
     * 取消完成后或任务完成后清理注册信息，避免内存泄漏
     * @param requestId 请求ID
     */
    public void unregister(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        inFlightFutures.remove(requestId);
        executingThreads.remove(requestId);
        cancelledIds.remove(requestId);
        timedOutIds.remove(requestId);
    }

    /**
     * 标记为已取消，并尝试取消正在执行的 Future 和直接中断执行线程。
     * 若该 requestId 对应任务尚未开始 LLM 调用，会在执行前通过 isCancelled 被跳过。
     * 如果任务正在执行中（如阻塞的LLM网络请求），会直接中断执行线程以响应中断。
     */
    public void cancel(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        cancelledIds.put(requestId, Boolean.TRUE);

        // 直接中断执行线程（优先级最高，用于中断阻塞I/O调用如LLM网络请求）
        interruptThread(requestId, "CANCEL");

        // 同时取消Future
        CompletableFuture<?> f = inFlightFutures.get(requestId);
        if (f != null) {
            boolean cancelled = f.cancel(true);
            if (cancelled) {
                log.info("已取消Future，requestId={}", requestId);
            } else {
                log.debug("Future取消失败，requestId={}（可能已完成）", requestId);
            }
        }
    }

    /**
     * 标记为超时，并中断执行线程，但不取消 Future（由上层决定返回超时响应）。
     */
    public void timeout(String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        timedOutIds.put(requestId, Boolean.TRUE);
        interruptThread(requestId, "TIMEOUT");
    }

    public boolean isCancelled(String requestId) {
        return requestId != null && Boolean.TRUE.equals(cancelledIds.get(requestId));
    }

    public boolean isTimedOut(String requestId) {
        return requestId != null && Boolean.TRUE.equals(timedOutIds.get(requestId));
    }

    private void interruptThread(String requestId, String reason) {
        Thread thread = executingThreads.get(requestId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("已直接中断执行线程，reason={}，requestId={}，thread={}", reason, requestId, thread.getName());
        }
    }
}
