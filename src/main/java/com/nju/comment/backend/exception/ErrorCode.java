package com.nju.comment.backend.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    //成功
    SUCCESS(0, "成功"),

    //系统级错误
    SYSTEM_ERROR(1001, "系统内部错误"),
    PARAMETER_ERROR(1002, "参数错误"),
    TIMEOUT_ERROR(1003, "请求超时"),
    RATE_LIMIT_EXCEEDED(1004, "请求频率过高"),

    //业务级错误,
    LLM_SERVICE_ERROR(2001, "LLM服务错误"),
    LLM_TIMEOUT(2002, "LLM服务超时"),
    LLM_UNAVAILABLE(2003, "LLM服务不可用"),
    LLM_INTERRUPTED(2004, "LLM服务被中断"),
    LLM_EXECUTION_ERROR(2005, "LLM执行错误"),

    PROMPTS_SERVICE_ERROR(3001, "提示词服务错误"),
    PROMPTS_BUILD_ERROR(3002, "提示词构建错误"),

    CACHE_SERVICE_ERROR(4001, "缓存服务错误"),
    CACHE_SIZE_GET_ERROR(4002, "缓存大小获取错误"),

    COMMENT_SERVICE_ERROR(5001, "注释服务错误"),
    COMMENT_REQUEST_NUM_EXCEEDED(5002, "注释请求数量超过限制");


    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
