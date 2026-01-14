package com.nju.comment.backend.config;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.api-key:}")
    private String apiKey;

    @Bean
    public OllamaApi ollamaApi() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(baseUrl);

        if (StringUtils.hasText(apiKey)) {
            restClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }

        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
    }
}