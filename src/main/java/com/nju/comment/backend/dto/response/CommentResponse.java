package com.nju.comment.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private boolean success;

    private String generatedComment;

    private String modelUsed;

    private Long processingTimeMs;

    private String requestId;

    private Instant timestamp;

    private String errorMessage;

    @Builder.Default
    private boolean cancelled = false;

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    public static CommentResponse success(String comment) {
        return CommentResponse.builder()
                .success(true)
                .generatedComment(comment)
                .timestamp(Instant.now())
                .build();
    }

    public static CommentResponse error(String error) {
        return CommentResponse.builder()
                .success(false)
                .errorMessage(error)
                .timestamp(Instant.now())
                .build();
    }

    public static CommentResponse cancelled(String requestId) {
        return CommentResponse.builder()
                .success(false)
                .cancelled(true)
                .errorMessage("请求已取消")
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();
    }

    public CommentResponse withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public CommentResponse withModelUsed(String model) {
        this.modelUsed = model;
        return this;
    }

    public CommentResponse withProcessingTime(Long timeMs) {
        this.processingTimeMs = timeMs;
        return this;
    }
}
