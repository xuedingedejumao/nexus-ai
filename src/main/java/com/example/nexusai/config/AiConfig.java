package com.example.nexusai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public EmbeddingStore<TextSegment> embeddingStore() {
        return MilvusEmbeddingStore.<TextSegment>builder()
                .uri("http://"+ milvusHost + ":" + milvusPort)
                .collectionName(collectionName)
                .dimension(512)
                .build();
    }
}
