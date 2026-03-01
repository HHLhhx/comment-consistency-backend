package com.nju.comment.backend.component;

import com.nju.comment.backend.config.OllamaConfig;
import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ollama 模型工厂
 * <p>
 * 按 (apiKeyHash + modelName) 缓存 ChatClient 实例，
 * 不同用户使用各自的 API Key 调用云端 Ollama 服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaModelFactory {

    private final CacheService cacheService;
    private final OllamaConfig ollamaConfig;

    @Getter
    private final EmbeddingModel embeddingModel;

    /** 缓存 key = apiKeyHash:modelName → ChatClient */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /** 缓存 apiKeyHash → OllamaApi，避免为同一 Key 重复构建 */
    private final Map<String, OllamaApi> apiCache = new ConcurrentHashMap<>();

    /**
     * 获取当前用户的 ChatClient（需确保 {@link UserApiContext} 已设置 API Key）
     */
    public ChatClient getChatModelClient(String modelName) {
        // 从 UserApiContext 获取 API Key，并进行基本校验
        String apiKey = UserApiContext.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.AUTH_API_KEY_NOT_SET);
        }

        // 计算 API Key 的短哈希，作为缓存 key 和日志标识
        String keyHash = shortHash(apiKey);
        String cacheKey = keyHash + ":" + modelName;

        // 使用 computeIfAbsent 保证线程安全的懒加载 ChatClient 实例
        return clientCache.computeIfAbsent(cacheKey, k -> {
            log.info("创建 Ollama ChatClient: model={}, keyHash={}", modelName, keyHash);
            OllamaApi ollamaApi = getOrCreateOllamaApi(apiKey, keyHash);
            OllamaChatModel chatModel = OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(OllamaOptions.builder().model(modelName).build())
                    .build();
            return ChatClient.builder(chatModel).build();
        });
    }

    /**
     * 获取可用模型列表（需确保 {@link UserApiContext} 已设置 API Key）
     */
    public List<String> getAvailableChatModels() {
        // 从 UserApiContext 获取 API Key，并进行基本校验
        String apiKey = UserApiContext.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.AUTH_API_KEY_NOT_SET);
        }

        try {
            List<String> models = cacheService.getModelsList("MODELS_LIST");
            if (models != null && !models.isEmpty()) {
                log.info("从缓存获取到 {} 个可用模型", models.size());
                return models;
            }

            String keyHash = shortHash(apiKey);
            OllamaApi ollamaApi = getOrCreateOllamaApi(apiKey, keyHash);

            OllamaApi.ListModelResponse listModelResponse = ollamaApi.listModels();
            if (listModelResponse.models() == null || listModelResponse.models().isEmpty()) {
                log.warn("未获取到任何可用模型");
                return Collections.emptyList();
            }

            log.info("获取到 {} 个可用模型", listModelResponse.models().size());
            List<String> modelsList = listModelResponse.models().stream()
                    .map(OllamaApi.Model::name)
                    .toList();

            cacheService.saveModelsList("MODELS_LIST", modelsList);
            return modelsList;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取可用模型列表失败", e);
            throw new LLMException(ErrorCode.LLM_MODEL_FETCH_ERROR, "获取LLM模型列表失败", e);
        }
    }

    /**
     * 按 API Key 获取或创建 OllamaApi 实例（带 Bearer 认证头）
     */
    private OllamaApi getOrCreateOllamaApi(String apiKey, String keyHash) {
        return apiCache.computeIfAbsent(keyHash, h -> {
            String baseUrl = ollamaConfig.getChatBaseUrl();
            log.info("创建 OllamaApi: baseUrl={}, keyHash={}", baseUrl, keyHash);
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
            return OllamaApi.builder()
                    .baseUrl(baseUrl)
                    .restClientBuilder(builder)
                    .build();
        });
    }

    /**
     * API Key 的短哈希（SHA-256 前 8 个十六进制字符），用作缓存 key 和日志标识
     */
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
