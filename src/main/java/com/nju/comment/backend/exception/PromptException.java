package com.nju.comment.backend.exception;

/**
 * 提示词服务相关异常
 */
public class PromptException extends ServiceException {

    public PromptException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PromptException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public PromptException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public PromptException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
