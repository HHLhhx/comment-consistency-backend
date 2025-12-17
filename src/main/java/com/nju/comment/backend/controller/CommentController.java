package com.nju.comment.backend.controller;

import com.nju.comment.backend.dto.request.CommentRequest;
import com.nju.comment.backend.dto.response.ApiResponse;
import com.nju.comment.backend.dto.response.CommentResponse;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.service.CommentBaseService;
import com.nju.comment.backend.service.CommentExtendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "注释生成", description = "智能注释生成api")
public class CommentController {

    private final CommentBaseService commentBaseService;

    private final CommentExtendService commentExtendService;

    @Operation(summary = "生成注释", description = "为给定代码生成注释")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<CommentResponse>> generateComment(
            @Valid @RequestBody CommentRequest commentRequest
    ) throws ExecutionException, InterruptedException {

        log.info("收到注释生成请求，语言：{}", commentRequest.getLanguage());

        CompletableFuture<ResponseEntity<ApiResponse<CommentResponse>>> result = commentBaseService.generateComment(commentRequest)
                .thenApply(response -> {
                    ApiResponse<CommentResponse> apiResponse = response.isSuccess()
                            ? ApiResponse.success("注释生成成功", response)
                            : ApiResponse.error(response.getErrorMessage(), ErrorCode.COMMENT_SERVICE_ERROR.getCode());

                    return ResponseEntity.status(response.isSuccess()
                                    ? HttpStatus.OK
                                    : HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(apiResponse);
                });

        return result.get();
    }

    @Operation(summary = "批量生成注释", description = "批量生成注释")
    @PostMapping("batch-generate")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> batchGenerateComments(
            @Valid @RequestBody List<@Valid CommentRequest> commentRequests
    ) throws ExecutionException, InterruptedException {

        log.info("收到批量注释生成请求，数量：{}", commentRequests.size());

        CompletableFuture<ResponseEntity<ApiResponse<List<CommentResponse>>>> result = commentExtendService.batchGenerateComments(commentRequests)
                .thenApply(response -> {
                    boolean allSuccess = response.stream().allMatch(CommentResponse::isSuccess);

                    ApiResponse<List<CommentResponse>> apiResponse = allSuccess
                            ? ApiResponse.success("批量生成成功", response)
                            : ApiResponse.error("部分注释生成失败", ErrorCode.COMMENT_SERVICE_ERROR.getCode());

                    return ResponseEntity.status(allSuccess
                                    ? HttpStatus.OK
                                    : HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(apiResponse);
                });

        return result.get();
    }

    @Operation(summary = "健康检查", description = "检查服务状态")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Boolean>> health() {
        boolean isHealthy = commentBaseService.isServiceHealthy();

        ApiResponse<Boolean> apiResponse = isHealthy
                ? ApiResponse.success("注释生成服务正常", true)
                : ApiResponse.error("注释生成服务不可用", HttpStatus.SERVICE_UNAVAILABLE);

        return ResponseEntity.status(isHealthy
                        ? HttpStatus.OK
                        : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiResponse);
    }
}

