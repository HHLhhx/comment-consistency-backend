package com.nju.comment.backend.component;

import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.CacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OllamaModelFactory {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private OllamaApi ollamaApi;

    @Value("classpath:prompts/prompt_system.st")
    private Resource systemPromptResource;

    private String systemPrompt;

    private final Map<String, ChatClient> modelCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = systemPromptResource.getInputStream()) {
            systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            systemPrompt = """
                    You are an AI Java comment updater, and your task is to update one method's comment based on the code modification.
                    The purpose of the update is to reflect the changes in the code while retaining all the unchanged parts.
                    Your updated comment will be directly used to substitute the original one.
                    """;
        }
    }

    public ChatClient getModel(String modelName) {
        return modelCache.computeIfAbsent(modelName, name -> {
            log.info("创建Ollama模型实例: {}", name);
            OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(
                            OllamaOptions.builder()
                                    .model(name)
                                    .build()
                    )
                    .build();
            return ChatClient.builder(ollamaChatModel)
                    .defaultSystem(systemPrompt)
                    .build();
        });
    }

    public void clearCache() {
        modelCache.clear();
    }

    public List<String> getAvailableModels() {
        try {
            List<String> models = cacheService.getModelsList("MODELS_LIST");
            if (models != null && !models.isEmpty()) {
                log.info("从缓存获取到 {} 个可用模型", models.size());
                return models;
            }

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
        } catch (Exception e) {
            log.error("获取可用模型列表失败", e);
            throw new ServiceException(ErrorCode.LLM_MODEL_FETCH_ERROR, e);
        }
    }
}
