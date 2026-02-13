package com.nju.comment.backend.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * 异常日志工具类
 * 提供统一的异常日志记录格式和方法
 */
@Slf4j
public class ExceptionLogUtil {

    private ExceptionLogUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 记录业务异常日志
     *
     * @param logger    日志记录器
     * @param operation 操作描述
     * @param requestId 请求ID
     * @param ex        异常对象
     */
    public static void logBusinessError(Logger logger, String operation, String requestId, Throwable ex) {
        logger.error("业务异常 - operation={}, requestId={}, errorType={}, message={}",
                operation, requestId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    /**
     * 记录业务警告日志
     *
     * @param logger    日志记录器
     * @param operation 操作描述
     * @param requestId 请求ID
     * @param message   警告信息
     */
    public static void logBusinessWarning(Logger logger, String operation, String requestId, String message) {
        logger.warn("业务警告 - operation={}, requestId={}, message={}",
                operation, requestId, message);
    }

    /**
     * 记录系统异常日志
     *
     * @param logger    日志记录器
     * @param operation 操作描述
     * @param ex        异常对象
     */
    public static void logSystemError(Logger logger, String operation, Throwable ex) {
        logger.error("系统异常 - operation={}, errorType={}, message={}",
                operation, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    /**
     * 记录外部服务调用异常日志
     *
     * @param logger      日志记录器
     * @param serviceName 服务名称
     * @param operation   操作描述
     * @param requestId   请求ID
     * @param duration    调用时长（毫秒）
     * @param ex          异常对象
     */
    public static void logExternalServiceError(Logger logger, String serviceName, 
                                               String operation, String requestId, 
                                               long duration, Throwable ex) {
        logger.error("外部服务异常 - service={}, operation={}, requestId={}, duration={}ms, errorType={}, message={}",
                serviceName, operation, requestId, duration, 
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    /**
     * 记录性能警告日志
     *
     * @param logger    日志记录器
     * @param operation 操作描述
     * @param requestId 请求ID
     * @param duration  操作时长（毫秒）
     * @param threshold 阈值（毫秒）
     */
    public static void logPerformanceWarning(Logger logger, String operation, 
                                            String requestId, long duration, long threshold) {
        logger.warn("性能警告 - operation={}, requestId={}, duration={}ms, threshold={}ms",
                operation, requestId, duration, threshold);
    }

    /**
     * 记录资源释放失败日志
     *
     * @param logger       日志记录器
     * @param resourceName 资源名称
     * @param ex           异常对象
     */
    public static void logResourceCleanupError(Logger logger, String resourceName, Throwable ex) {
        logger.warn("资源释放失败 - resource={}, errorType={}, message={}",
                resourceName, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
}
