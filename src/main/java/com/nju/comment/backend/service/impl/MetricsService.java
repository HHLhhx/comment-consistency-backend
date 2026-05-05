package com.nju.comment.backend.service.impl;

import com.nju.comment.backend.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务指标采集服务
 * <p>
 * 把 LLM、注释生成、错误码等业务事件转成 Prometheus 指标。<br>
 * 计数器采用懒加载（{@link Counter.Builder#register}），避免在 {@link MeterRegistry} 中
 * 注册大量未使用的标签组合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /** 当前正在执行的 LLM 调用数量（gauge） */
    private final AtomicInteger inflightLlmRequests = new AtomicInteger(0);

    /* ========== 初始化 ========== */

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("llm.request.inflight", inflightLlmRequests);
    }

    /* ========== LLM 链路 ========== */

    /** 一次 LLM 调用进入活跃态，返回 {@link Timer.Sample} 用于在结束时上报耗时 */
    public Timer.Sample startLlmCall() {
        inflightLlmRequests.incrementAndGet();
        return Timer.start(meterRegistry);
    }

    /**
     * 结束 LLM 调用并上报指标。
     *
     * @param sample    {@link #startLlmCall()} 的返回值
     * @param model     模型名（高基数标签时建议在外部白名单化）
     * @param outcome   "success" / "failure" / "interrupted" / "timeout"
     * @param errorCode 失败场景下的错误码（成功时传 null）
     */
    public void recordLlmCall(Timer.Sample sample, String model, String outcome, ErrorCode errorCode) {
        try {
            sample.stop(Timer.builder("llm.request.duration")
                    .description("LLM 调用耗时")
                    .tags(Tags.of(
                            "model", nullSafe(model),
                            "outcome", outcome
                    ))
                    .publishPercentileHistogram()
                    .register(meterRegistry));

            Counter.builder("llm.request.total")
                    .description("LLM 调用总次数")
                    .tags(Tags.of(
                            "model", nullSafe(model),
                            "outcome", outcome,
                            "error", errorCode == null ? "none" : errorCode.name()
                    ))
                    .register(meterRegistry)
                    .increment();
        } finally {
            inflightLlmRequests.decrementAndGet();
        }
    }

    /* ========== 注释生成链路 ========== */

    /**
     * 上报一次注释生成结果。
     *
     * @param outcome  "success" / "cached" / "canceled" / "timeout" / "failure"
     * @param duration 注释生成耗时（含等待 + 执行）
     */
    public void recordCommentGeneration(String outcome, String model, Duration duration) {
        Timer.builder("comment.generation.duration")
                .description("注释生成端到端耗时")
                .tags("outcome", outcome, "model", nullSafe(model))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);

        Counter.builder("comment.generation.total")
                .description("注释生成总次数")
                .tags("outcome", outcome, "model", nullSafe(model))
                .register(meterRegistry)
                .increment();
    }

    /** 注释缓存命中（简单计数，缓存框架自身的指标 Spring 已自动暴露） */
    public void recordCommentCacheHit() {
        Counter.builder("comment.cache.hit.total")
                .description("注释缓存命中次数")
                .register(meterRegistry)
                .increment();
    }

    /* ========== 错误码 ========== */

    /** 错误码统一计数（在 GlobalExceptionHandler 中调用） */
    public void recordError(ErrorCode errorCode, String uri) {
        Counter.builder("app.error.total")
                .description("按错误码统计的业务异常次数")
                .tags(
                        "code", errorCode.name(),
                        "http_status", String.valueOf(errorCode.getHttpStatus().value()),
                        "uri", normalizeUri(uri)
                )
                .register(meterRegistry)
                .increment();
    }

    /* ========== 工具 ========== */

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * 简单归一化 URI：去掉路径中的纯数字段（如 /api/foo/123 → /api/foo/{id}），
     * 避免标签基数爆炸。
     */
    private static String normalizeUri(String uri) {
        if (uri == null || uri.isEmpty()) return "unknown";
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
