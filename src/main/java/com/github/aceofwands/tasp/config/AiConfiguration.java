package com.github.aceofwands.tasp.config;

import com.github.aceofwands.tasp.ai.OllamaDecisionModel;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfiguration {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean("entityDecisionModel")
    public DecisionModel entityDecisionModel(
            @Value("${tasp.entity-resolution.classifier.base-url:http://localhost:11434}") String baseUrl,
            @Value("${tasp.entity-resolution.classifier.model:clef-flash}") String model,
            @Value("${tasp.entity-resolution.classifier.timeout:30s}") Duration timeout
    ) {
        var httpClient = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        return new OllamaDecisionModel(httpClient, baseUrl, model);
    }
}