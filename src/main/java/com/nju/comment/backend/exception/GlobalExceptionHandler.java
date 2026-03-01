package com.nju.comment.backend.exception;

import com.nju.comment.backend.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 * 统一处理所有异常，规范化错误响应格式
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");

        log.warn("参数校验失败: uri={}, error={}", request.getRequestURI(), errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorMessage, ErrorCode.PARAMETER_ERROR.getCode()));
    }

    /**
     * 处理参数约束异常（@Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(err -> err.getPropertyPath() + ": " + err.getMessage())
                .findFirst()
                .orElse("参数约束校验失败");

        log.warn("参数约束校验失败: uri={}, error={}", request.getRequestURI(), errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorMessage, ErrorCode.PARAMETER_ERROR.getCode()));
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleTimeoutException(
            TimeoutException ex,
            HttpServletRequest request
    ) {
        log.warn("请求超时: uri={}, message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error("请求处理超时", ErrorCode.TIMEOUT_ERROR.getCode()));
    }

    /**
     * 处理异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncTimeoutException(
            AsyncRequestTimeoutException ex,
            HttpServletRequest request
    ) {
        log.warn("异步请求超时: uri={}, message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error("请求处理超时", ErrorCode.LLM_TIMEOUT.getCode()));
    }

    /**
     * 处理取消异常
     */
    @ExceptionHandler(CancellationException.class)
    public ResponseEntity<ApiResponse<Object>> handleCancellationException(
            CancellationException ex,
            HttpServletRequest request
    ) {
        log.info("请求被取消: uri={}, message={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .ok()
                .body(ApiResponse.error("请求已取消", ErrorCode.COMMENT_REQUEST_CANCELLED.getCode()));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleServiceException(
            ServiceException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();
        String requestId = ex.getRequestId();

        // 根据错误级别选择日志级别
        if (isWarningLevel(errorCode)) {
            log.warn("业务警告: uri={}, code={}, message={}, requestId={}, data={}",
                    request.getRequestURI(), errorCode.getCode(), ex.getMessage(), requestId, ex.getData());
        } else {
            log.error("业务异常: uri={}, code={}, message={}, requestId={}, data={}",
                    request.getRequestURI(), errorCode.getCode(), ex.getMessage(), requestId, ex.getData(), ex);
        }

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), errorCode.getCode()));
    }

    /**
     * 处理异步完成异常（解包内部异常）
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiResponse<Object>> handleCompletionException(
            CompletionException ex,
            HttpServletRequest request
    ) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        // 解包后分发到对应的异常处理器
        if (cause instanceof ServiceException) {
            return handleServiceException((ServiceException) cause, request);
        } else if (cause instanceof TimeoutException) {
            return handleTimeoutException((TimeoutException) cause, request);
        } else if (cause instanceof CancellationException) {
            return handleCancellationException((CancellationException) cause, request);
        } else {
            log.error("异步处理异常: uri={}", request.getRequestURI(), cause);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("异步处理失败: " + cause.getMessage(),
                            ErrorCode.SYSTEM_ERROR.getCode()));
        }
    }

    /**
     * 处理Redis连接异常
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleRedisConnectionException(
            RedisConnectionFailureException ex,
            HttpServletRequest request
    ) {
        log.error("Redis连接失败: uri={}, error={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("缓存服务暂时不可用", ErrorCode.REDIS_CONNECTION_ERROR.getCode()));
    }

    /**
     * 处理Redis系统异常
     */
    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<ApiResponse<Object>> handleRedisSystemException(
            RedisSystemException ex,
            HttpServletRequest request
    ) {
        log.error("Redis操作异常: uri={}, error={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("缓存操作失败", ErrorCode.REDIS_OPERATION_ERROR.getCode()));
    }

    /**
     * 处理Spring Security认证异常
     * 当AuthenticationException未在Service层被捕获时，由此兜底处理
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        String message;
        if (ex instanceof BadCredentialsException) {
            message = ErrorCode.AUTH_LOGIN_FAILED.getMessage();
        } else {
            message = "认证失败: " + ex.getMessage();
        }

        log.warn("认证异常: uri={}, error={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(message, ErrorCode.AUTH_LOGIN_FAILED.getCode()));
    }

    /**
     * 处理其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("系统未知异常: uri={}", request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统内部错误", ErrorCode.SYSTEM_ERROR.getCode()));
    }

    /**
     * 判断错误码是否应该输出警告级别日志（而非错误级别）
     */
    private boolean isWarningLevel(ErrorCode errorCode) {
        return errorCode == ErrorCode.PARAMETER_ERROR
                || errorCode == ErrorCode.LLM_INTERRUPTED
                || errorCode == ErrorCode.COMMENT_REQUEST_CANCELLED
                || errorCode == ErrorCode.RATE_LIMIT_EXCEEDED
                || errorCode == ErrorCode.RESOURCE_NOT_FOUND
                || errorCode == ErrorCode.AUTH_LOGIN_FAILED
                || errorCode == ErrorCode.AUTH_USERNAME_EXISTS;
    }
}

