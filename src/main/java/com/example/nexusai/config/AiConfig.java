package com.example.nexusai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 基础设施配置：Embedding 模型、向量存储、Rerank 评分模型、ES 客户端。
 */
@Configuration
public class AiConfig {

    /** BGE-small-zh 输出维度 */
    private static final int EMBEDDING_DIMENSION = 512;
    private static final int KNN_NUM_CANDIDATES = 100;
    private static final int CHAT_MEMORY_MAX_MESSAGES = 10;

    @Value("${nexus.elasticsearch.host}")
    private String esHost;

    @Value("${nexus.elasticsearch.port}")
    private int esPort;

    @Value("${nexus.elasticsearch.index-name}")
    private String indexName;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    /**
     * 基于 Bi-Encoder 余弦相似度的 Pseudo-Reranker。
     * <p>
     * 工程权衡：真正的 Cross-Encoder Reranker（如 BGE-Reranker）排序精度更高，
     * 但需要额外部署 ONNX Runtime 或调用外部 API，增加运维复杂度和延迟。
     * 本方案复用已有的 Embedding 模型计算余弦相似度作为二次打分，
     * 在"召回数量不大（Top-20）"的场景下，精度损失可接受，但部署零成本。
     */
    @Bean
    public ScoringModel scoringModel(EmbeddingModel embeddingModel) {
        return new ScoringModel() {
            @Override
            public Response<Double> score(String text, String query) {
                double score = CosineSimilarity.between(
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

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return ElasticsearchEmbeddingStore.builder()
                .serverUrl("http://" + esHost + ":" + esPort)
                .indexName(indexName)
                .dimension(EMBEDDING_DIMENSION)
                .configuration(ElasticsearchConfigurationKnn.builder()
                        .numCandidates(KNN_NUM_CANDIDATES)
                        .build())
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
                new HttpHost(esHost, esPort, "http")).build();
        return new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return sessionId -> MessageWindowChatMemory.withMaxMessages(CHAT_MEMORY_MAX_MESSAGES);
    }
}
