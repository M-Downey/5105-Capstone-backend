package com.example.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.model}") String model
    ) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.model}") String model
    ) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.rag.embedding-model}") String embeddingModel
    ) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}








