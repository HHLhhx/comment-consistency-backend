package com.nju.comment.backend.exception;

import com.nju.comment.backend.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import jakarta.validation.ConstraintViolationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数错误");

        log.error("参数合法验证失败: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.error(errorMessage, ErrorCode.PARAMETER_ERROR.getCode()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(err -> err.getPropertyPath() + ": " + err.getMessage())
                .findFirst()
                .orElse("参数错误");

        log.error("参数约束验证失败: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.error(errorMessage, ErrorCode.PARAMETER_ERROR.getCode()));
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleTimeoutException(TimeoutException ex) {
        log.error("请求处理超时", ex);

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error(ex.getMessage(), ErrorCode.TIMEOUT_ERROR.getCode()));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncTimeoutException(AsyncRequestTimeoutException ex) {
        log.error("异步请求处理超时", ex);

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error("请求处理超时", ErrorCode.LLM_TIMEOUT.getCode()));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleServiceException(ServiceException e) {
        log.error("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());

        return ResponseEntity.status(getHttpStatus(e.getErrorCode()))
                .body(ApiResponse.error(e.getMessage(), e.getErrorCode().getCode()));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiResponse<Object>> handleCompletionException(CompletionException e) {
        Throwable cause = e.getCause();

        if (cause instanceof ServiceException) {
            return handleServiceException((ServiceException) cause);
        } else if (cause instanceof TimeoutException) {
            return handleTimeoutException((TimeoutException) cause);
        } else {
            log.error("异步处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("异步处理失败", ErrorCode.SYSTEM_ERROR.getCode()));
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        log.error("服务器内部错误", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误", ErrorCode.SYSTEM_ERROR.getCode()));
    }

    private HttpStatusCode getHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case SUCCESS ->  HttpStatus.OK;
            case PARAMETER_ERROR -> HttpStatus.BAD_REQUEST;
            case LLM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT_ERROR, LLM_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
