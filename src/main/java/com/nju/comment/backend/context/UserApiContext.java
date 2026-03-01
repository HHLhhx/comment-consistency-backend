package com.nju.comment.backend.context;

/**
 * 用户 API Key 的线程上下文
 * <p>
 * 在异步线程中传递当前用户的 Ollama API Key，
 * 使用前在请求线程中通过 {@link #setApiKey} 设置，异步完成后通过 {@link #clear} 清理。
 */
public final class UserApiContext {

    private static final ThreadLocal<String> API_KEY = new ThreadLocal<>();

    private UserApiContext() {
    }

    public static void setApiKey(String apiKey) {
        API_KEY.set(apiKey);
    }

    public static String getApiKey() {
        return API_KEY.get();
    }

    public static void clear() {
        API_KEY.remove();
    }
}
