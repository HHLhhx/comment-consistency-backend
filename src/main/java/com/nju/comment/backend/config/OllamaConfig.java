package com.nju.comment.backend.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfig {

    @Value("${app.ai.ollama.chat.base-url:http://localhost:11434}")
    private String chatBaseUrl;

    @Value("${app.ai.ollama.chat.api-key:}")
    private String chatApiKey;

    @Bean("chatOllamaApi")
    @Primary
    public OllamaApi chatOllamaApi() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(chatBaseUrl);

        if (StringUtils.hasText(chatApiKey)) {
            restClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + chatApiKey.trim());
        }

        return OllamaApi.builder()
                .baseUrl(chatBaseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
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
    public EmbeddingModel embeddingModel(@Qualifier("embeddingOllamaApi") OllamaApi embeddingOllamaApi) {
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