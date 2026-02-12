package com.example.nexusai.service;

import com.example.nexusai.entity.ChatHistory;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.mapper.ChatHistoryMapper;
import com.example.nexusai.utils.UserUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;
    private final ElasticsearchClient elasticsearchClient;
    private final KnowledgeAgentFactory agentFactory;
    private final ChatHistoryMapper chatHistoryMapper;
    private final SemanticCacheService semanticCacheService;
    private final UserUtils userUtils;

    @Value("${nexus.elasticsearch.index-name}")
    private String indexName;

    /** 文档入库：分片 → 向量化 → 存入 Elasticsearch */
    public void ingest(String content, String filename) {
        Document document = Document.from(content, Metadata.from("filename", filename));
        List<TextSegment> segments = DocumentSplitters.recursive(500, 100).split(document);
        log.info("文档分割完成，段落数：{}", segments.size());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        log.info("文档入库完成");
    }

    /** 普通对话接口 */
    @RateLimiter(name = "chatApi")
    public String chat(String query, ModelType modelType, String sessionId) {
        try {
            log.info("收到用户[{}]问题：{}, 使用模型：{}", sessionId, query, modelType.getName());

            // 1. 语义缓存查询
            if (!isContextDependent(query)) {
                Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
                if (cachedAnswer.isPresent()) {
                    String answer = cachedAnswer.get();
                    log.info("语义缓存命中，直接返回结果");
                    insertChatHistory(sessionId, query, answer, modelType);
                    return answer;
                }
            }

            // 2. RAG 检索与生成
            String context = retrieveContext(query);
            KnowledgeAgent agent = agentFactory.getAgent(modelType);

            long startTime = System.currentTimeMillis();
            String answer = agent.answer(sessionId, query, context);
            long duration = System.currentTimeMillis() - startTime;
            log.info("模型回答完成，耗时：{} ms", duration);

            // 3. 回写语义缓存
            if (!isContextDependent(query)) {
                semanticCacheService.setCachedAnswer(query, answer);
            }

            // 4. 持久化到数据库
            insertChatHistory(sessionId, query, answer, modelType);
            return answer;

        } catch (Exception e) {
            log.error("查询失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /** 流式对话接口 */
    public SseEmitter streamChat(String query, ModelType modelType, String sessionId) {
        SseEmitter sseEmitter = new SseEmitter(5 * 60 * 1000L);

        // 1. 语义缓存查询
        if (!isContextDependent(query)) {
            Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
            if (cachedAnswer.isPresent()) {
                String answer = cachedAnswer.get();
                log.info("语义缓存命中，直接返回结果");
                insertChatHistory(sessionId, query, answer, modelType);

                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(answer));
                        sseEmitter.send(SseEmitter.event().data("[DONE]"));
                        sseEmitter.complete();
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                });
                return sseEmitter;
            }
        }

        // 2. 缓存未命中，执行正常流式逻辑
        String context = retrieveContext(query);
        TokenStream tokenStream = agentFactory.getStreamAgent(modelType).chat(sessionId, query, context);
        StringBuilder contentBuilder = new StringBuilder();

        // 保存安全上下文，防止异步线程丢失
        var securityContext = SecurityContextHolder.getContext();
        var requestAttributes = RequestContextHolder.getRequestAttributes();

        tokenStream
                .onNext(token -> {
                    try {
                        contentBuilder.append(token);
                        sseEmitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                })
                .onComplete(token -> {
                    SecurityContextHolder.setContext(securityContext);
                    RequestContextHolder.setRequestAttributes(requestAttributes);

                    try {
                        String fullAnswer = contentBuilder.toString();

                        if (!fullAnswer.trim().isEmpty() && !isContextDependent(query)) {
                            log.info("流式输出结束，写入语义缓存");
                            semanticCacheService.setCachedAnswer(query, fullAnswer);
                        }

                        log.info("流式输出结束，保存到数据库");
                        insertChatHistory(sessionId, query, fullAnswer, modelType);
                        sseEmitter.complete();
                    } finally {
                        SecurityContextHolder.clearContext();
                        RequestContextHolder.resetRequestAttributes();
                    }
                })
                .onError(sseEmitter::completeWithError)
                .start();

        return sseEmitter;
    }

    /**
     * 混合检索上下文 (Hybrid Search: BM25 + kNN Vector + RRF 融合 + Rerank 精排)
     *
     * 流程：BM25 倒排索引 + kNN 向量搜索 → RRF 融合排序 → Top-20 粗排 → Rerank 精排 → Top-3
     */
    private String retrieveContext(String queryText) {
        try {
            // 1. 生成查询向量
            List<Float> queryVector = embeddingModel.embed(queryText).content().vectorAsList();

            // 2. BM25 倒排索引查询
            Query matchQuery = MatchQuery.of(m -> m.field("text").query(queryText))._toQuery();

            // 3. kNN 向量查询
            KnnSearch knnSearch = KnnSearch.of(k -> k
                    .field("vector")
                    .queryVector(queryVector)
                    .numCandidates(100)
                    .k(20));

            // 4. 执行混合搜索 (RRF 融合排序)
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(matchQuery)
                    .knn(knnSearch)
                    .rank(r -> r
                            .rrf(rrf -> rrf
                                    .windowSize(20)
                                    .rankConstant(60)))
                    .size(20),
                    Map.class);

            if (response.hits().hits().isEmpty()) {
                return "";
            }

            // 5. 解析结果转换为 TextSegment
            List<TextSegment> candidates = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null && source.containsKey("text")) {
                    String text = (String) source.get("text");
                    Metadata metadata = new Metadata();
                    if (source.containsKey("metadata")) {
                        Map<String, Object> metaMap = (Map<String, Object>) source.get("metadata");
                        metaMap.forEach((k, v) -> metadata.add(k, v.toString()));
                    }
                    candidates.add(TextSegment.from(text, metadata));
                }
            }

            if (candidates.isEmpty())
                return "";

            // 6. 二次精排 (Rerank Top-20 → Top-3)
            List<Double> scores = scoringModel.scoreAll(candidates, queryText).content();

            var ranked = IntStream.range(0, candidates.size())
                    .mapToObj(i -> new Object() {
                        final TextSegment segment = candidates.get(i);
                        final Double score = scores.get(i);
                    })
                    .sorted(Comparator.comparingDouble(o -> -o.score))
                    .limit(3)
                    .toList();

            // 7. 拼接最终上下文
            List<String> contextList = ranked.stream()
                    .map(item -> {
                        String text = item.segment.text();
                        String filename = item.segment.metadata().get("filename");
                        return String.format("[来源：%s (Score: %.2f)] %s", filename, item.score, text);
                    })
                    .collect(Collectors.toList());

            return String.join("\n---\n", contextList);

        } catch (IOException e) {
            log.error("Elasticsearch 检索失败", e);
            return "";
        }
    }

    /** 判断查询是否包含上下文敏感词 (包含则跳过语义缓存) */
    private boolean isContextDependent(String query) {
        if (query == null)
            return true;
        String q = query.toLowerCase();
        String[] sensitiveKeywords = {
                "我", "你", "您", "谁", "它", "他", "她",
                "my", "i ", "you", "who", "this", "that"
        };
        for (String keyword : sensitiveKeywords) {
            if (q.contains(keyword)) {
                log.info("检测到上下文敏感词 '{}'，跳过语义缓存", keyword);
                return true;
            }
        }
        return false;
    }

    /** 保存对话历史到数据库 */
    private void insertChatHistory(String sessionId, String question, String answer, ModelType modelType) {
        Long currentUserId = userUtils.getCurrentUserId();
        ChatHistory chatHistory = new ChatHistory()
                .setSession_id(sessionId)
                .setUserId(currentUserId)
                .setUser_query(question)
                .setAi_answer(answer)
                .setModel_type(modelType.getName())
                .setCreate_time(java.time.LocalDateTime.now());
        chatHistoryMapper.insert(chatHistory);
    }
}
