package com.nju.comment.backend.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 用于封装业务层的异常，统一返回错误码和错误信息
 */
@Getter
public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;
    private final String requestId;

    public ServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = null;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
        this.requestId = null;
    }

    public ServiceException(ErrorCode errorCode, String message, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
        this.requestId = requestId;
    }

    public ServiceException(ErrorCode errorCode, String message, Object data, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
        this.requestId = requestId;
    }
}
