package com.nju.comment.backend.component;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.LLMException;
import com.nju.comment.backend.service.CacheService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaModelFactory {

    private final CacheService cacheService;

    private final OllamaApi chatOllamaApi;

    private final Map<String, ChatClient> modelCache = new ConcurrentHashMap<>();

    @Getter
    private final EmbeddingModel embeddingModel;

    public ChatClient getChatModelClient(String modelName) {
        return modelCache.computeIfAbsent(modelName, name -> {
            log.info("创建Ollama模型实例: {}", name);
            OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                    .ollamaApi(chatOllamaApi)
                    .defaultOptions(
                            OllamaOptions.builder()
                                    .model(name)
                                    .build()
                    )
                    .build();
            return ChatClient.builder(ollamaChatModel)
                    .build();
        });
    }

    public List<String> getAvailableChatModels() {
        try {
            List<String> models = cacheService.getModelsList("MODELS_LIST");
            if (models != null && !models.isEmpty()) {
                log.info("从缓存获取到 {} 个可用模型", models.size());
                return models;
            }

            OllamaApi.ListModelResponse listModelResponse = chatOllamaApi.listModels();
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
        } catch (Exception e) {
            log.error("获取可用模型列表失败", e);
            throw new LLMException(ErrorCode.LLM_MODEL_FETCH_ERROR, "获取LLM模型列表失败", e);
        }
    }
}
