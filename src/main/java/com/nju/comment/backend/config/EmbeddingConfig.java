package com.nju.comment.backend.config;

import com.nju.comment.backend.component.embedding.RateLimitedEmbeddingModel;
import com.nju.comment.backend.component.embedding.SiliconFlowEmbeddingRateLimiter;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class EmbeddingConfig {

    @Value("${app.ai.siliconflow.embedding.base-url:https://api.siliconflow.cn}")
    private String siliconFlowEmbeddingBaseUrl;

    @Value("${app.ai.siliconflow.embedding.api-key:}")
    private String siliconFlowEmbeddingApiKey;

    @Value("${app.ai.siliconflow.embedding.model:BAAI/bge-m3}")
    private String siliconFlowEmbeddingModel;

    @Value("${app.ai.siliconflow.embedding.embeddings-path:/v1/embeddings}")
    private String siliconFlowEmbeddingsPath;

    @Value("${app.ai.siliconflow.embedding.max-retries:5}")
    private int embeddingMaxRetries;

    @Value("${app.ai.siliconflow.embedding.initial-backoff-ms:1000}")
    private long embeddingInitialBackoffMs;

    @Value("${app.ai.siliconflow.embedding.max-backoff-ms:10000}")
    private long embeddingMaxBackoffMs;

    @Bean("siliconFlowEmbeddingApi")
    public OpenAiApi siliconFlowEmbeddingApi() {
        if (!StringUtils.hasText(siliconFlowEmbeddingApiKey)) {
            throw new IllegalStateException("app.ai.siliconflow.embedding.api-key is required");
        }

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(siliconFlowEmbeddingBaseUrl);

        return OpenAiApi.builder()
                .baseUrl(siliconFlowEmbeddingBaseUrl)
                .apiKey(siliconFlowEmbeddingApiKey.trim())
                .embeddingsPath(siliconFlowEmbeddingsPath)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Primary
    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi siliconFlowEmbeddingApi,
                                         SiliconFlowEmbeddingRateLimiter rateLimiter) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(siliconFlowEmbeddingModel)
                .build();
        OpenAiEmbeddingModel delegate = new OpenAiEmbeddingModel(
                siliconFlowEmbeddingApi,
                MetadataMode.NONE,
                options
        );
        return new RateLimitedEmbeddingModel(
                delegate,
                rateLimiter,
                embeddingMaxRetries,
                embeddingInitialBackoffMs,
                embeddingMaxBackoffMs
        );
    }
}