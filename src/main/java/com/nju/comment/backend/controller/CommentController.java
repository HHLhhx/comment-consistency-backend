package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.CancelRequest;
import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.service.CommentService;
import com.nju.comment.backend.service.LLMService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

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
                .handle((response, ex) -> {
                    if (ex != null) {
                        // 处理异常情况
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

                        // 检查是否是取消异常
                        if (cause instanceof CancellationException) {
                            log.info("注释生成请求被取消");
                            CommentResponse cancelledResponse = CommentResponse.cancelled(
                                    commentRequest.getRequestId());
                            return ResponseEntity.ok(
                                    ApiResponse.success("请求已取消", cancelledResponse));
                        }

                        // 检查是否是超时异常
                        if (cause instanceof TimeoutException) {
                            log.info("注释生成请求超时");
                            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                                    .body(ApiResponse.error("LLM调用超时",
                                            ErrorCode.LLM_TIMEOUT.getCode()));
                        }

                        // 其他异常
                        log.error("注释生成请求异常", cause);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("服务异常: " + cause.getMessage(),
                                        ErrorCode.COMMENT_SERVICE_ERROR.getCode()));
                    }

                    // 正常响应
                    ApiResponse<CommentResponse> apiResponse = response.isSuccess()
                            ? ApiResponse.success("注释生成成功", response)
                            : ApiResponse.error(response.getErrorMessage(),
                            ErrorCode.COMMENT_SERVICE_ERROR.getCode());

                    return ResponseEntity.status(response.isSuccess()
                                    ? HttpStatus.OK
                                    : HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(apiResponse);
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

