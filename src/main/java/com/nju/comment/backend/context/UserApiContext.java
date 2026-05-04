package com.nju.comment.backend.context;

/**
 * 用户 LLM 凭证的线程上下文
 * <p>
 * 用于在异步线程中传递当前用户调用 OpenAI 协议兼容 LLM 时的 API Key 与 Base URL，
 * 不同用户/不同供应商可使用各自的凭证。<br>
 * 在请求线程中通过 {@link #setCredential(String, String)} 设置，
 * 异步完成后通过 {@link #clear()} 清理，避免线程池场景下的泄漏。
 */
public final class UserApiContext {

    private static final ThreadLocal<String> API_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> BASE_URL = new ThreadLocal<>();

    private UserApiContext() {
    }

    /**
     * 一次性设置 API Key 与 Base URL。
     */
    public static void setCredential(String apiKey, String baseUrl) {
        API_KEY.set(apiKey);
        BASE_URL.set(baseUrl);
    }

    public static String getApiKey() {
        return API_KEY.get();
    }

    public static String getBaseUrl() {
        return BASE_URL.get();
    }

    public static void clear() {
        API_KEY.remove();
        BASE_URL.remove();
    }
}
