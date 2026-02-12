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
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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

    /** 本地 BGE 中文向量模型 (ONNX, 512维) */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    /** 基于 Embedding 余弦相似度的二次打分模型 (Pseudo-Reranker) */
    @Bean
    public ScoringModel scoringModel(EmbeddingModel embeddingModel) {
        return new ScoringModel() {
            @Override
            public Response<Double> score(String text, String query) {
                double score = dev.langchain4j.store.embedding.CosineSimilarity.between(
                        embeddingModel.embed(text).content(),
                        embeddingModel.embed(query).content());
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

    /** Elasticsearch 向量存储 (用于文档入库) */
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

    /** Elasticsearch 原生客户端 (用于 BM25 混合检索) */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
                new HttpHost(esHost, esPort, "http")).build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    /** 对话记忆提供器，每个会话保留最近 10 条消息 */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return sessionId -> MessageWindowChatMemory.withMaxMessages(10);
    }
}
