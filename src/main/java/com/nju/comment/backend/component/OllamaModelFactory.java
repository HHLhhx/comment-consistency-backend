package com.nju.comment.backend.component;

import com.nju.comment.backend.context.UserApiContext;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
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

    @Value("${app.ai.ollama.chat.base-url:http://localhost:11434}")
    private String chatBaseUrl;

    /**
     * 缓存 key = apiKeyHash:modelName → ChatClient
     */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 缓存 apiKeyHash → OllamaApi
     */
    private final Map<String, OllamaApi> apiCache = new ConcurrentHashMap<>();

    /**
     * 获取当前用户的 ChatClient（需确保 {@link UserApiContext} 已设置 API Key）
     */
    public ChatClient getChatModelClient(String modelName) {
        // 从 UserApiContext 获取 API Key，并进行基本校验
        String apiKey = UserApiContext.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
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
            throw new ServiceException(ErrorCode.LLM_API_KEY_NOT_SET);
        }

        try {
            // 先尝试从缓存获取模型列表，避免频繁调用 Ollama API
            List<String> models = cacheService.getModelsList("MODELS_LIST");
            if (models != null && !models.isEmpty()) {
                log.info("从缓存获取到 {} 个可用模型", models.size());
                return models;
            }

            // 计算 API Key 的短哈希，作为缓存 key 和日志标识
            String keyHash = shortHash(apiKey);
            OllamaApi ollamaApi = getOrCreateOllamaApi(apiKey, keyHash);

            // 调用 Ollama API 获取模型列表
            OllamaApi.ListModelResponse listModelResponse = ollamaApi.listModels();
            if (listModelResponse.models() == null || listModelResponse.models().isEmpty()) {
                log.warn("未获取到任何可用模型");
                return Collections.emptyList();
            }

            // 提取模型名称列表，并缓存结果
            List<String> modelsList = listModelResponse.models().stream()
                    .map(OllamaApi.Model::name)
                    .filter(model -> modelWhiteList.contains(model))
                    .toList();
            log.info("获取到 {} 个可用模型", modelsList.size());

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
            String baseUrl = this.chatBaseUrl;
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

    static List<String> modelWhiteList = List.of(
            "qwen3-coder:480b",
            "nemotron-3-nano:30b",
            "glm-4.7",
            "kimi-k2:1t",
            "gpt-oss:120b",
            "mistral-large-3:675b",
            "gemma3:27b",
            "cogito-2.1:671b",
            "gemma3:12b",
            "qwen3-coder-next",
            "devstral-2:123b",
            "rnj-1:8b",
            "deepseek-v3.1:671b",
            "ministral-3:14b",
            "nemotron-3-super",
            "gpt-oss:20b",
            "minimax-m2.5",
            "ministral-3:3b",
            "ministral-3:8b",
            "devstral-small-2:24b"
    );
}
