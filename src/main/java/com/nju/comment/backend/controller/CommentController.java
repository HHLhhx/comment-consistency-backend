package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.CancelRequest;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.service.CommentService;
import com.nju.comment.backend.service.LLMService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CommentController {

    private final CommentService commentService;
    private final LLMService llmService;

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<ApiResponse<CommentResponse>>> generateComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CommentRequest commentRequest
    ) {
        log.info("收到注释生成请求，使用模型：{}", commentRequest.getModelName());

        return commentService.generateComment(userDetails.getUsername(), commentRequest)
                .thenApply(response -> {
                    if (response.isCancelled()) {
                        return ResponseEntity.ok(
                                ApiResponse.success("请求已取消", response));
                    }
                    return ResponseEntity.ok(
                            ApiResponse.success("注释生成成功", response));
                });
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelGenerate(@Valid @RequestBody CancelRequest cancelRequest) {
        commentService.cancel(cancelRequest);
        return ResponseEntity.ok(ApiResponse.success("后台已执行取消请求",null));
    }

    @GetMapping("/models")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableModels(@AuthenticationPrincipal UserDetails userDetails) {
        List<String> availableModels = llmService.getAvailableModels(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(availableModels));
    }
}

