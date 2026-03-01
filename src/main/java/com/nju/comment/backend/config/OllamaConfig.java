package com.nju.comment.backend.config;

import lombok.Getter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import com.nju.comment.backend.util.ApiKeyEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfig {

    @Getter
    @Value("${app.ai.ollama.chat.base-url:http://localhost:11434}")
    private String chatBaseUrl;

    @Value("${app.security.api-key-encrypt-key}")
    private String apiKeyEncryptKey;

    /**
     * API Key 加解密器，供 UserApiKeyService 使用
     */
    @Bean
    public ApiKeyEncryptor apiKeyEncryptor() {
        return new ApiKeyEncryptor(apiKeyEncryptKey);
    }

    @Value("${app.ai.ollama.embedding.base-url:http://localhost:11434}")
    private String embeddingBaseUrl;

    @Value("${app.ai.ollama.embedding.model:qwen3-embedding:0.6b}")
    private String embeddingModelName;

    @Bean("embeddingOllamaApi")
    public OllamaApi embeddingOllamaApi() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(embeddingBaseUrl);

        return OllamaApi.builder()
                .baseUrl(embeddingBaseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(OllamaApi embeddingOllamaApi) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(embeddingOllamaApi)
                .defaultOptions(
                        OllamaOptions.builder()
                                .model(embeddingModelName)
                                .build()
                )
                .modelManagementOptions(
                        ModelManagementOptions.builder()
                                .pullModelStrategy(PullModelStrategy.ALWAYS)
                                .build()
                )
                .build();
    }
}