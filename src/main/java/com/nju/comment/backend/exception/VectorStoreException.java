package com.nju.comment.backend.exception;

/**
 * 向量存储相关异常
 */
public class VectorStoreException extends ServiceException {

    public VectorStoreException(ErrorCode errorCode) {
        super(errorCode);
    }

    public VectorStoreException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public VectorStoreException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public VectorStoreException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
