package com.nju.comment.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 错误码枚举
 * <p>
 * 错误码规范:<br>
 * - 0: 成功<br>
 * - 1xxx: 系统级错误（参数、超时、限流等）<br>
 * - 2xxx: LLM服务相关错误<br>
 * - 3xxx: 提示词服务相关错误<br>
 * - 4xxx: 缓存服务相关错误<br>
 * - 5xxx: 注释业务相关错误<br>
 * - 6xxx: 向量存储相关错误<br>
 * - 7xxx: 认证授权相关错误<br>
 */
@Getter
public enum ErrorCode {

    // ========== 成功 ==========
    SUCCESS(0, "操作成功", HttpStatus.OK),

    // ========== 系统级错误 1xxx ==========
    SYSTEM_ERROR(1001, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAMETER_ERROR(1002, "参数错误", HttpStatus.BAD_REQUEST),
    TIMEOUT_ERROR(1003, "请求超时", HttpStatus.REQUEST_TIMEOUT),
    RATE_LIMIT_EXCEEDED(1004, "请求频率过高，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    UNAUTHORIZED(1005, "未授权访问", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1006, "权限不足", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(1007, "资源不存在", HttpStatus.NOT_FOUND),

    // ========== LLM服务错误 2xxx ==========
    LLM_SERVICE_ERROR(2001, "LLM服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    LLM_TIMEOUT(2002, "LLM调用超时", HttpStatus.REQUEST_TIMEOUT),
    LLM_UNAVAILABLE(2003, "LLM服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    LLM_INTERRUPTED(2004, "LLM调用被中断", HttpStatus.OK),
    LLM_EXECUTION_ERROR(2005, "LLM执行失败", HttpStatus.INTERNAL_SERVER_ERROR),
    LLM_MODEL_FETCH_ERROR(2006, "获取LLM模型列表失败", HttpStatus.INTERNAL_SERVER_ERROR),
    LLM_MODEL_NOT_FOUND(2007, "指定的LLM模型不存在", HttpStatus.BAD_REQUEST),
    LLM_CONNECTION_ERROR(2008, "LLM连接失败", HttpStatus.SERVICE_UNAVAILABLE),

    // ========== 提示词服务错误 3xxx ==========
    PROMPT_SERVICE_ERROR(3001, "提示词服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    PROMPT_BUILD_ERROR(3002, "提示词构建失败", HttpStatus.INTERNAL_SERVER_ERROR),
    PROMPT_TEMPLATE_READ_ERROR(3003, "提示词模板读取失败", HttpStatus.INTERNAL_SERVER_ERROR),
    PROMPT_TEMPLATE_NOT_FOUND(3004, "提示词模板不存在", HttpStatus.INTERNAL_SERVER_ERROR),

    // ========== 缓存服务错误 4xxx ==========
    CACHE_SERVICE_ERROR(4001, "缓存服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_READ_ERROR(4002, "缓存读取失败", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_WRITE_ERROR(4003, "缓存写入失败", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_CLEAR_ERROR(4004, "缓存清理失败", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_CONNECTION_ERROR(4005, "Redis连接失败", HttpStatus.SERVICE_UNAVAILABLE),
    REDIS_OPERATION_ERROR(4006, "Redis操作异常", HttpStatus.INTERNAL_SERVER_ERROR),

    // ========== 注释业务错误 5xxx ==========
    COMMENT_SERVICE_ERROR(5001, "注释服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    COMMENT_REQUEST_INVALID(5002, "注释请求参数不完整或不合法", HttpStatus.BAD_REQUEST),
    COMMENT_GENERATION_FAILED(5003, "注释生成失败", HttpStatus.INTERNAL_SERVER_ERROR),
    COMMENT_REQUEST_CANCELLED(5004, "注释请求已被取消", HttpStatus.OK),

    // ========== 向量存储错误 6xxx ==========
    VECTOR_STORE_ERROR(6001, "向量数据库异常", HttpStatus.INTERNAL_SERVER_ERROR),
    VECTOR_STORE_INIT_ERROR(6002, "向量数据库初始化失败", HttpStatus.INTERNAL_SERVER_ERROR),
    VECTOR_STORE_QUERY_ERROR(6003, "向量检索失败", HttpStatus.INTERNAL_SERVER_ERROR),
    VECTOR_STORE_CONNECTION_ERROR(6004, "向量数据库连接失败", HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_STORE_INTERRUPTED(6005, "向量数据库操作被中断", HttpStatus.OK),

    // ========== 认证授权错误 7xxx ==========
    AUTH_LOGIN_FAILED(7001, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    AUTH_USERNAME_EXISTS(7002, "用户名已存在", HttpStatus.BAD_REQUEST),
    AUTH_TOKEN_EXPIRED(7003, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID(7004, "无效的认证令牌", HttpStatus.UNAUTHORIZED),
    AUTH_NOT_LOGGED_IN(7005, "未登录，请先登录", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_DENIED(7006, "权限不足，拒绝访问", HttpStatus.FORBIDDEN),
    AUTH_LOGOUT_SUCCESS(7007, "已成功登出", HttpStatus.OK),
    AUTH_TOKEN_BLACKLISTED(7008, "令牌已失效，请重新登录", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 根据错误码获取对应的枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
