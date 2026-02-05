package com.example.nexusai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class AiConfig {

    @Value("${nexus.elasticsearch.host}")
    private String esHost;

    @Value("${nexus.elasticsearch.port}")
    private int esPort;

    @Value("${nexus.elasticsearch.index-name}")
    private String indexName;

    @Bean
    public EmbeddingModel embeddingModel(){
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    @Bean
    public ScoringModel scoringModel(EmbeddingModel embeddingModel) {
        return new ScoringModel() {
            @Override
            public Response<Double> score(String text, String query) {
                double score = dev.langchain4j.store.embedding.CosineSimilarity.between(
                        embeddingModel.embed(text).content(),
                        embeddingModel.embed(query).content()
                );
                return Response.from(score);
            }

            @Override
            public Response<Double> score(TextSegment segment, String query) {
                return score(segment.text(), query);
            }

            @Override
            public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
                List<Double> scores = segments.stream()
                        .map(segment -> score(segment, query).content())
                        .collect(Collectors.toList());
                return Response.from(scores);
            }
        };
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return ElasticsearchEmbeddingStore.builder()
                .serverUrl("http://" + esHost + ":" + esPort)
                .indexName(indexName)
                .dimension(512)
                .configuration(ElasticsearchConfigurationKnn.builder()
                        .numCandidates(100)
                        .build())
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        return sessionId -> MessageWindowChatMemory.withMaxMessages(10);
    }
}
