package com.example.nexusai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Slf4j
@Service
public class SemanticCacheService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JedisPooled jedis;

    private static final int VECTOR_DIM = 512;
    private static final String PREFIX = "cache:";
    private static final String INDEX_NAME = "nexus_cache_idx";
    private static final double SIMILARITY_THRESHOLD = 0.90;

    @PostConstruct
    public void initIndex() {
        try {
            jedis.ftInfo(INDEX_NAME);
            log.info("Redis 索引 '{}' 已存在", INDEX_NAME);
        } catch (Exception e) {
            log.info("正在创建 Redis 索引 '{}'...", INDEX_NAME);
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
            log.info("Redis 索引创建成功");
        } catch (Exception e) {
            log.error("Redis 索引创建失败: {}", e.getMessage());
        }
    }

    /** 查询语义缓存 (L1 Caffeine → L2 Redis 向量检索) */
    @Cacheable(value = "localCache", key = "#userQuestion", unless = "#result == null")
    public Optional<String> getCachedAnswer(String userQuestion) {
        log.info("L1 本地缓存未命中，查询 L2 Redis 语义缓存...");
        try {
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            if (floatVector.length != VECTOR_DIM) {
                log.warn("向量维度不匹配！预期: {}, 实际: {}", VECTOR_DIM, floatVector.length);
                return Optional.empty();
            }

            Query query = new Query("*=>[KNN 1 @vector $vec AS score]")
                    .addParam("vec", floatsToBytes(floatVector))
                    .returnFields("answer", "question", "score")
                    .dialect(2);

            SearchResult result = jedis.ftSearch(INDEX_NAME, query);

            if (result.getTotalResults() > 0) {
                Document doc = result.getDocuments().get(0);

                Object scoreObj = doc.get("score");
                double score = 1.0;
                if (scoreObj != null) {
                    try {
                        score = Double.parseDouble(scoreObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("分数格式无效: {}", scoreObj);
                    }
                }

                // Redis 余弦距离: 0(完全相同) → 1(完全不同)，转换为相似度
                double similarity = 1 - score;
                log.info("缓存候选结果: 距离={}, 相似度={}", score, similarity);

                if (similarity >= SIMILARITY_THRESHOLD) {
                    log.info("语义缓存命中！问题: {}", userQuestion);
                    return Optional.ofNullable(doc.getString("answer"));
                }
            }
        } catch (Exception e) {
            log.error("缓存查询异常: {}", e.getMessage());
        }

        log.info("语义缓存未命中");
        return Optional.empty();
    }

    /** 将问答对存入语义缓存 */
    public void setCachedAnswer(String userQuestion, String aiAnswer) {
        try {
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            Map<String, Object> fields = new HashMap<>();
            fields.put("question", userQuestion);
            fields.put("answer", aiAnswer);
            fields.put("vector", floatsToBytes(floatVector));

            String key = PREFIX + UUID.randomUUID().toString();
            jedis.hset(key, (Map) fields);
            log.info("已缓存问答对, Key: {}", key);
        } catch (Exception e) {
            log.error("缓存写入失败: {}", e.getMessage());
        }
    }

    /** float[] 转 byte[] (Little Endian) */
    private byte[] floatsToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
}