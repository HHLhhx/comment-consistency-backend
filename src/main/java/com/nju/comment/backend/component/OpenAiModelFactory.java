package com.nju.comment.backend.component;

import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 协议兼容模型工厂
 * <p>
 * 支持任意符合 OpenAI Chat Completions / Models 协议的供应商
 * （OpenAI、DeepSeek、阿里 DashScope 兼容模式、Moonshot、智谱 BigModel 等）。<br>
 * 按 (apiKeyHash + baseUrlHash + modelName) 缓存 ChatClient，使不同用户/不同供应商互不干扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiModelFactory {

    private final CacheService cacheService;

    @Value("${app.ai.openai.chat.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    @Value("${app.ai.openai.chat.completions-path:/v1/chat/completions}")
    private String completionsPath;

    @Value("${app.ai.openai.chat.models-path:/v1/models}")
    private String modelsPath;

    /** 缓存 key = apiKeyHash:baseUrlHash:modelName → ChatClient */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /** 缓存 apiKeyHash:baseUrlHash → OpenAiApi */
    private final Map<String, OpenAiApi> apiCache = new ConcurrentHashMap<>();

    /**
     * 获取当前用户的 ChatClient（需确保 {@link UserApiContext} 已设置 API Key）。
     */
    public ChatClient getChatModelClient(String modelName) {
        String apiKey = requireApiKey();
        String baseUrl = resolveBaseUrl();

        String keyHash = shortHash(apiKey);
        String urlHash = shortHash(baseUrl);
        String cacheKey = keyHash + ":" + urlHash + ":" + modelName;

        return clientCache.computeIfAbsent(cacheKey, k -> {
            log.info("创建 OpenAI ChatClient: model={}, baseUrl={}, keyHash={}",
                    modelName, baseUrl, keyHash);
            OpenAiApi openAiApi = getOrCreateOpenAiApi(apiKey, baseUrl, keyHash, urlHash);
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();
            return ChatClient.builder(chatModel).build();
        });
    }

    /**
     * 获取可用模型列表（需确保 {@link UserApiContext} 已设置 API Key）。
     * <p>
     * 通过 OpenAI 协议标准 {@code /v1/models} 端点请求，
     * 不同供应商可能返回的字段略有差异，此处仅取 {@code data[].id}。
     */
    public List<String> getAvailableChatModels() {
        String apiKey = requireApiKey();
        String baseUrl = resolveBaseUrl();

        // 模型列表与具体 baseUrl 绑定，缓存 key 中包含 baseUrl 哈希避免串供应商
        String urlHash = shortHash(baseUrl);
        String cacheTag = "MODELS_LIST:" + urlHash;

        try {
            List<String> cached = cacheService.getModelsList(cacheTag);
            if (cached != null && !cached.isEmpty()) {
                log.info("从缓存获取到 {} 个可用模型 (baseUrl={})", cached.size(), baseUrl);
                return cached;
            }

            List<String> modelsList = fetchModelsFromRemote(apiKey, baseUrl);
            log.info("获取到 {} 个可用模型 (baseUrl={})", modelsList.size(), baseUrl);
            cacheService.saveModelsList(cacheTag, modelsList);
            return modelsList;
        } catch (ServiceException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // 4xx/5xx HTTP 错误 → 转成业务异常便于全局 handler 输出友好信息
            int status = e.getStatusCode().value();
            log.warn("获取模型列表失败 status={} body={}", status, e.getResponseBodyAsString());
            throw mapHttpStatusToException(status,
                    "获取LLM模型列表失败: HTTP " + status, e);
        } catch (ResourceAccessException e) {
            log.error("获取模型列表网络错误，baseUrl={}", baseUrl, e);
            throw new LLMException(ErrorCode.LLM_CONNECTION_ERROR,
                    "无法连接 LLM 服务: " + baseUrl, e);
        } catch (Exception e) {
            log.error("获取可用模型列表失败", e);
            throw new LLMException(ErrorCode.LLM_MODEL_FETCH_ERROR,
                    "获取LLM模型列表失败", e);
        }
    }

    /**
     * 按 (apiKey, baseUrl) 获取或创建 OpenAiApi 实例。
     */
    private OpenAiApi getOrCreateOpenAiApi(String apiKey, String baseUrl,
                                           String keyHash, String urlHash) {
        String cacheKey = keyHash + ":" + urlHash;
        return apiCache.computeIfAbsent(cacheKey, h -> {
            log.info("创建 OpenAiApi: baseUrl={}, keyHash={}", baseUrl, keyHash);
            return OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey.trim())
                    .completionsPath(completionsPath)
                    .build();
        });
    }

    /**
     * 调用远端 {@code /v1/models}，提取 {@code data[].id}。
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchModelsFromRemote(String apiKey, String baseUrl) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .build();

        Map<String, Object> response = client.get()
                .uri(modelsPath)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null) {
            return Collections.emptyList();
        }
        Object data = response.get("data");
        if (!(data instanceof List<?>)) {
            log.warn("模型列表响应缺少 data 字段: {}", response);
            return Collections.emptyList();
        }
        return ((List<Map<String, Object>>) data).stream()
                .map(m -> m.get("id"))
                .filter(id -> id instanceof String)
                .map(Object::toString)
                .toList();
    }

    private String requireApiKey() {
        String apiKey = UserApiContext.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
        }
        return apiKey;
    }

    /**
     * 优先使用用户上下文中的 baseUrl，回退到全局默认。
     */
    private String resolveBaseUrl() {
        String userBase = UserApiContext.getBaseUrl();
        String base = (userBase != null && !userBase.isBlank()) ? userBase : defaultBaseUrl;
        if (base == null || base.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_BASE_URL_INVALID,
                    "未配置 LLM Base URL");
        }
        // 去掉末尾 /，避免 baseUrl + "/v1/..." 出现 "//"
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** 把 HTTP 状态码映射为业务异常 */
    private LLMException mapHttpStatusToException(int status, String defaultMsg, Throwable cause) {
        return switch (status) {
            case 401, 403 -> new LLMException(ErrorCode.LLM_API_KEY_INVALID,
                    "API Key 无效或权限不足", cause);
            case 404 -> new LLMException(ErrorCode.LLM_MODEL_NOT_FOUND,
                    "请求的接口或模型不存在", cause);
            case 429 -> new LLMException(ErrorCode.LLM_RATE_LIMIT,
                    "LLM 服务限流，请稍后重试", cause);
            default -> new LLMException(ErrorCode.LLM_SERVICE_ERROR, defaultMsg, cause);
        };
    }

    /** SHA-256 前 8 位十六进制 */
    private static String shortHash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
