package com.example.nexusai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import jakarta.annotation.PostConstruct; // Spring Boot 3 使用 jakarta
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Slf4j
@Service
public class SemanticCacheService {

    @Autowired
    private EmbeddingModel embeddingModel; // 自动注入 POM 中的 BGE 模型

    @Autowired
    private JedisPooled jedis; // 确保你有配置 JedisPooled 的 Bean

    private static final int VECTOR_DIM = 512;
    private static final String PREFIX = "cache:";
    private static final String INDEX_NAME = "nexus_cache_idx";
    private static final double SIMILARITY_THRESHOLD = 0.90;

    @PostConstruct
    public void initIndex() {
        try {
            // 检查索引是否存在
            jedis.ftInfo(INDEX_NAME);
            log.info("Redis Index '{}' already exists.", INDEX_NAME);
        } catch (Exception e) {
            log.info("Creating Redis Index '{}'...", INDEX_NAME);
            createIndex();
        }
    }

    private void createIndex() {
        try {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("TYPE", "FLOAT32");
            attributes.put("DIM", VECTOR_DIM);
            attributes.put("DISTANCE_METRIC", "COSINE");

            SchemaField[] schemaFields = {
                    TextField.of("question").weight(1.0).as("question"),
                    TextField.of("answer").weight(1.0).as("answer"),
                    VectorField.builder()
                            .fieldName("vector")
                            .algorithm(VectorField.VectorAlgorithm.HNSW)
                            .attributes(attributes)
                            .build()
            };

            FTCreateParams createParams = FTCreateParams.createParams()
                    .on(IndexDataType.HASH)
                    .addPrefix(PREFIX);

            jedis.ftCreate(INDEX_NAME, createParams, schemaFields);
            log.info("Index Created Successfully!");
        } catch (Exception e) {
            log.error("Failed to create index: {}", e.getMessage());
        }
    }

    /**
     * 尝试从缓存获取答案
     */
    public Optional<String> getCachedAnswer(String userQuestion) {
        try {
            // 1. 向量化用户问题
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            // ⚠️ 校验维度：防止维度不匹配报错
            if (floatVector.length != VECTOR_DIM) {
                log.warn("Embedding dim mismatch! Expected: {}, Actual: {}", VECTOR_DIM, floatVector.length);
                // 可以在这里根据实际情况调整 VECTOR_DIM，或者抛出异常
                return Optional.empty();
            }

            // 2. 构建查询
            // KNN 1 表示只取最相似的那 1 个
            Query query = new Query("*=>[KNN 1 @vector $vec AS score]")
                    .addParam("vec", floatsToBytes(floatVector))
                    .returnFields("answer", "question", "score")
                    .dialect(2);

            // 3. 执行搜索
            SearchResult result = jedis.ftSearch(INDEX_NAME, query);

            if (result.getTotalResults() > 0) {
                Document doc = result.getDocuments().get(0);
                double score = Double.parseDouble(doc.getString("score"));

                // Redis Cosine Distance: 0 (相同) -> 1 (完全不同)
                // 也就是 距离越小越相似。
                // 转换成相似度 = 1 - 距离
                double similarity = 1 - score;

                log.info("Cache Hit Candidate: score(dist)={}, similarity={}", score, similarity);

                if (similarity >= SIMILARITY_THRESHOLD) {
                    log.info("Semantic Cache HIT! Question: {}", userQuestion);
                    return Optional.ofNullable(doc.getString("answer"));
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving from cache: {}", e.getMessage());
        }

        log.info("Semantic Cache MISS.");
        return Optional.empty();
    }

    /**
     * 将新的问答对存入缓存
     */
    public void setCachedAnswer(String userQuestion, String aiAnswer) {
        try {
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            Map<String, Object> fields = new HashMap<>();
            fields.put("question", userQuestion);
            fields.put("answer", aiAnswer);
            fields.put("vector", floatsToBytes(floatVector));

            // Key 格式: cache:UUID
            String key = PREFIX + UUID.randomUUID().toString();
            jedis.hset(key, (Map) fields); // 注意强转或使用 Jedis 具体 API

            log.info("Cached new Q&A pair. Key: {}", key);
        } catch (Exception e) {
            log.error("Failed to set cache: {}", e.getMessage());
        }
    }

    /**
     * 工具方法：float[] 转 byte[] (Little Endian)
     */
    private byte[] floatsToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
}