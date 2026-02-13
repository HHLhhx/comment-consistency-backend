package com.nju.comment.backend.exception;

/**
 * LLM服务相关异常
 */
public class LLMException extends ServiceException {

    public LLMException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LLMException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public LLMException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public LLMException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public LLMException(ErrorCode errorCode, String message, String requestId) {
        super(errorCode, message, requestId);
    }
}
