package com.example.nexusai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 两级语义缓存服务：L1(Caffeine 本地) + L2(Redis Stack VSS 向量检索)。
 * <p>
 * 核心思路：将用户提问向量化后存入 Redis，查询时通过 KNN 近似最近邻找到语义最相似的历史问答对。
 * 对比传统的精确匹配缓存，语义缓存能命中"同义不同词"的重复查询，显著提高缓存命中率。
 * <p>
 * L1 由 Spring Cache(@Cacheable) + Caffeine 驱动，L1 MISS 后才进入本类的 Redis VSS 查询（L2），
 * 从而避免高频查询反复穿透到 Redis。
 */
@Slf4j
@Service
public class SemanticCacheService {

    /** BGE-small-zh 模型输出维度，必须与 embeddingModel 保持一致 */
    private static final int VECTOR_DIM = 512;
    private static final String KEY_PREFIX = "cache:";
    private static final String INDEX_NAME = "nexus_cache_idx";

    /**
     * 语义相似度命中阈值。Redis VSS 返回的是 Cosine Distance（0=相同, 1=完全不同），
     * 转换后 similarity = 1 - distance。阈值 0.90 意味着至少 90% 语义相似才视为缓存命中，
     * 过低会导致语义漂移（返回无关答案），过高会降低命中率。
     */
    private static final double SIMILARITY_THRESHOLD = 0.90;

    private final EmbeddingModel embeddingModel;
    private final JedisPooled jedis;

    public SemanticCacheService(EmbeddingModel embeddingModel, JedisPooled jedis) {
        this.embeddingModel = embeddingModel;
        this.jedis = jedis;
    }

    @PostConstruct
    public void initIndex() {
        try {
            jedis.ftInfo(INDEX_NAME);
            log.info("Redis 向量索引 '{}' 已存在", INDEX_NAME);
        } catch (Exception e) {
            log.info("初始化 Redis 向量索引 '{}'", INDEX_NAME);
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
                    .addPrefix(KEY_PREFIX);

            jedis.ftCreate(INDEX_NAME, createParams, schemaFields);
            log.info("Redis 向量索引创建成功");
        } catch (Exception e) {
            log.error("Redis 向量索引创建失败", e);
        }
    }

    /**
     * L1(Caffeine) + L2(Redis VSS) 两级缓存查询。
     * {@code @Cacheable} 拦截后，只有 L1 MISS 时才进入本方法体执行 Redis 向量检索。
     */
    @Cacheable(value = "localCache", key = "#userQuestion", unless = "#result == null")
    public Optional<String> getCachedAnswer(String userQuestion) {
        log.info("L1 缓存未命中, 查询 L2 语义缓存");
        try {
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            if (floatVector.length != VECTOR_DIM) {
                log.warn("向量维度不匹配, 期望: {}, 实际: {}", VECTOR_DIM, floatVector.length);
                return Optional.empty();
            }

            Query query = new Query("*=>[KNN 1 @vector $vec AS score]")
                    .addParam("vec", floatsToBytes(floatVector))
                    .returnFields("answer", "question", "score")
                    .dialect(2);

            SearchResult result = jedis.ftSearch(INDEX_NAME, query);

            if (result.getTotalResults() > 0) {
                redis.clients.jedis.search.Document doc = result.getDocuments().get(0);

                double distance = parseScore(doc.get("score"));
                double similarity = 1 - distance;

                log.info("L2 缓存候选命中: distance={}, similarity={}", distance, similarity);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    log.info("语义缓存命中, question={}", userQuestion);
                    return Optional.ofNullable(doc.getString("answer"));
                }
            }
        } catch (Exception e) {
            log.error("语义缓存查询异常, question={}", userQuestion, e);
        }

        log.info("L2 语义缓存未命中");
        return Optional.empty();
    }

    public void setCachedAnswer(String userQuestion, String aiAnswer) {
        try {
            Embedding embedding = embeddingModel.embed(userQuestion).content();
            float[] floatVector = embedding.vector();

            Map<String, Object> fields = new HashMap<>();
            fields.put("question", userQuestion);
            fields.put("answer", aiAnswer);
            fields.put("vector", floatsToBytes(floatVector));

            String key = KEY_PREFIX + UUID.randomUUID();
            @SuppressWarnings("unchecked")
            Map<String, String> stringFields = (Map) fields;
            jedis.hset(key, stringFields);

            log.info("语义缓存写入成功, key={}", key);
        } catch (Exception e) {
            log.error("语义缓存写入失败, question={}", userQuestion, e);
        }
    }

    private double parseScore(Object scoreObj) {
        if (scoreObj == null) {
            return 1.0;
        }
        try {
            return Double.parseDouble(scoreObj.toString());
        } catch (NumberFormatException e) {
            log.warn("无效的分数格式: {}", scoreObj);
            return 1.0;
        }
    }

    private byte[] floatsToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
}