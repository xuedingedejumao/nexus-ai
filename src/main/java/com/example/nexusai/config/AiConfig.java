package com.example.nexusai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfig {

    @Value("${nexus.milvus.host}")
    private String milvusHost;

    @Value("${nexus.milvus.port}")
    private int milvusPort;

    @Value("${nexus.milvus.collection-name}")
    private String collectionName;

    @Bean
    public EmbeddingModel embeddingModel(){
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    @Bean
    public ScoringModel scoringModel(EmbeddingModel embeddingModel) {
        return new ScoringModel() {
            @Override
            public Double score(String text, String query) {
                return (double) dev.langchain4j.store.embedding.CosineSimilarity.between(
                        embeddingModel.embed(text).content(),
                        embeddingModel.embed(query).content()
                );
            }

            @Override
            public List<Double> scoreAll(List<TextSegment> segments, String query) {
                return segments.stream()
                        .map(segment -> score(segment.text(), query))
                        .toList();
            }
        };
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return MilvusEmbeddingStore.<TextSegment>builder()
                .uri("http://"+ milvusHost + ":" + milvusPort)
                .collectionName(collectionName)
                .dimension(512)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        return sessionId -> MessageWindowChatMemory.withMaxMessages(10);
    }
}
