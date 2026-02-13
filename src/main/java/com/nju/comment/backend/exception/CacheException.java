package com.nju.comment.backend.exception;

/**
 * 缓存服务相关异常
 */
public class CacheException extends ServiceException {

    public CacheException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CacheException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CacheException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CacheException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
