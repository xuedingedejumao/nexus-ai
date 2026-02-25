package com.example.nexusai.service;

import com.example.nexusai.entity.ChatHistory;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.mapper.ChatHistoryMapper;
import com.example.nexusai.utils.UserUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG 核心服务：实现"检索 → 粗排 → 精排 → 生成"四阶段流水线。
 * <p>
 * 架构要点：
 * <ul>
 * <li>缓存策略采用 L1(Caffeine) + L2(Redis VSS) 两级穿透，通过语义相似度而非精确匹配来命中缓存，
 * 有效降低大模型 API 调用频次。对含上下文代词的查询主动跳过缓存，避免缓存污染。</li>
 * <li>检索采用 BM25 + kNN 双路召回，通过应用层 RRF 融合排序（而非 ES 服务端 RRF），
 * 规避 elasticsearch-java 8.x 客户端与不同版本 ES 服务端之间 RRF API 字段名不兼容的问题。</li>
 * <li>精排阶段复用 Bi-Encoder（BGE-small）计算余弦相似度作为 Pseudo-Reranker，
 * 在避免引入 Cross-Encoder 额外部署成本的前提下，显著提升上下文相关性。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    /** 文档分片大小（token 数），需要与 Embedding 模型的最大输入长度匹配 */
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;

    /** SSE 流式连接超时时间，5 分钟足够覆盖 DeepSeek-Reasoner 的最长推理耗时 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    /** RRF 融合常量 k=60 是学术界推荐的默认值（Cormack et al., 2009），值越大越平滑 */
    private static final int RRF_RANK_CONSTANT = 60;

    /** 双路召回各取 Top-N，N 过大会引入噪声，过小会漏召回 */
    private static final int RECALL_TOP_K = 20;

    /** kNN 候选池大小，需远大于 RECALL_TOP_K 以保证 ANN 召回精度 */
    private static final int KNN_NUM_CANDIDATES = 100;

    /** 精排后保留的上下文条数，受制于 LLM 上下文窗口和 Prompt 长度预算 */
    private static final int RERANK_TOP_K = 3;

    /** 前端 SSE 流结束标志 */
    private static final String SSE_DONE_SIGNAL = "[DONE]";

    private static final String[] CONTEXT_SENSITIVE_KEYWORDS = {
            "我", "你", "您", "谁", "它", "他", "她",
            "my", "i ", "you", "who", "this", "that"
    };

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;
    private final ElasticsearchClient elasticsearchClient;
    private final KnowledgeAgentFactory agentFactory;
    private final ChatHistoryMapper chatHistoryMapper;
    private final SemanticCacheService semanticCacheService;
    private final UserUtils userUtils;

    /**
     * 共享线程池，用于缓存命中时的异步 SSE 推送。
     * 避免每次请求通过 Executors.newSingleThreadExecutor() 创建临时线程池导致线程泄露。
     */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-cache-push");
        t.setDaemon(true);
        return t;
    });

    @Value("${nexus.elasticsearch.index-name}")
    private String indexName;

    public void ingest(String content, String filename) {
        Document document = Document.from(content, Metadata.from("filename", filename));
        List<TextSegment> segments = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP).split(document);
        log.info("文档分割完成, 段落数: {}", segments.size());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        log.info("文档 [{}] 向量化入库完成", filename);
    }

    @RateLimiter(name = "chatApi")
    public String chat(String query, ModelType modelType, String sessionId) {
        try {
            log.info("收到用户[{}]问题: {}, 模型: {}", sessionId, query, modelType.getName());

            if (!isContextDependent(query)) {
                Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
                if (cachedAnswer.isPresent()) {
                    log.info("语义缓存命中, sessionId={}", sessionId);
                    insertChatHistory(sessionId, query, cachedAnswer.get(), modelType);
                    return cachedAnswer.get();
                }
            }

            String context = retrieveContext(query);
            KnowledgeAgent agent = agentFactory.getAgent(modelType);

            long startTime = System.currentTimeMillis();
            // TODO 压测分支暂用硬编码回答，正式环境应调用 agent.answer(sessionId, query, context)
            String answer = "压测专用回答，实际环境请调用 agent.answer() 方法生成答案。";
            log.info("模型回答完成, 耗时: {} ms", System.currentTimeMillis() - startTime);

            if (!isContextDependent(query)) {
                semanticCacheService.setCachedAnswer(query, answer);
            }

            insertChatHistory(sessionId, query, answer, modelType);
            return answer;

        } catch (Exception e) {
            log.error("查询失败, query={}, sessionId={}", query, sessionId, e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 流式对话：通过 SSE 逐 token 推送给前端，解决大模型长耗时场景下的用户体验问题。
     * <p>
     * 注意：LangChain4j 的 TokenStream 回调运行在异步线程中，Spring Security 上下文会丢失，
     * 因此需要在主线程提前捕获 SecurityContext，在回调中手动恢复，完成后清理，防止上下文泄露。
     */
    public SseEmitter streamChat(String query, ModelType modelType, String sessionId) {
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (!isContextDependent(query)) {
            Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
            if (cachedAnswer.isPresent()) {
                String answer = cachedAnswer.get();
                log.info("语义缓存命中(流式), sessionId={}", sessionId);
                insertChatHistory(sessionId, query, answer, modelType);

                sseExecutor.submit(() -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(answer));
                        sseEmitter.send(SseEmitter.event().data(SSE_DONE_SIGNAL));
                        sseEmitter.complete();
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                });
                return sseEmitter;
            }
        }

        String context = retrieveContext(query);
        TokenStream tokenStream = agentFactory.getStreamAgent(modelType).chat(sessionId, query, context);

        StringBuilder contentBuilder = new StringBuilder();
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
                            semanticCacheService.setCachedAnswer(query, fullAnswer);
                        }

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
     * 三级检索流水线：BM25 + kNN 双路召回 → RRF 融合粗排 → Embedding Rerank 精排。
     * <p>
     * 设计选型说明：
     * <ul>
     * <li>BM25 擅长精确关键词匹配（如专业术语、型号编码），kNN 擅长语义相似度匹配，
     * 两者互补能显著提升非标准化表述的召回率。</li>
     * <li>RRF 融合不依赖分数归一化，对异构检索引擎的分数分布差异具有天然鲁棒性，
     * 因此优于简单的线性加权融合。</li>
     * <li>精排使用 Bi-Encoder 余弦相似度而非 Cross-Encoder，牺牲约 5-10% 的排序精度，
     * 换取 10 倍以上的推理速度提升，适合在线服务的延迟要求。</li>
     * </ul>
     *
     * @param queryText 用户原始查询文本
     * @return 拼接后的上下文字符串，包含来源和相关性分数
     */
    @SuppressWarnings("unchecked")
    private String retrieveContext(String queryText) {
        try {
            List<Float> queryVector = embeddingModel.embed(queryText).content().vectorAsList();

            // BM25 倒排索引召回
            Query matchQuery = MatchQuery.of(m -> m.field("text").query(queryText))._toQuery();
            SearchResponse<Map> bm25Response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(matchQuery)
                    .size(RECALL_TOP_K),
                    Map.class);

            // kNN 向量召回
            KnnSearch knnSearch = KnnSearch.of(k -> k
                    .field("vector")
                    .queryVector(queryVector)
                    .numCandidates(KNN_NUM_CANDIDATES)
                    .k(RECALL_TOP_K));
            SearchResponse<Map> knnResponse = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .knn(knnSearch)
                    .size(RECALL_TOP_K),
                    Map.class);

            // 应用层 RRF 融合：score(d) = Σ 1 / (k + rank_i)，同一文档在两路中的排名贡献累加
            LinkedHashMap<String, Double> rrfScores = new LinkedHashMap<>();
            LinkedHashMap<String, Map<String, Object>> docSources = new LinkedHashMap<>();

            accumulateRrfScores(bm25Response.hits().hits(), rrfScores, docSources);
            accumulateRrfScores(knnResponse.hits().hits(), rrfScores, docSources);

            List<String> topDocIds = rrfScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(RECALL_TOP_K)
                    .map(Map.Entry::getKey)
                    .toList();

            if (topDocIds.isEmpty()) {
                return "";
            }

            List<TextSegment> candidates = new ArrayList<>();
            for (String docId : topDocIds) {
                Map<String, Object> source = docSources.get(docId);
                if (source != null && source.containsKey("text")) {
                    String text = (String) source.get("text");
                    Metadata metadata = new Metadata();
                    if (source.containsKey("metadata")) {
                        Map<String, Object> metaMap = (Map<String, Object>) source.get("metadata");
                        metaMap.forEach((k, v) -> metadata.put(k, v.toString()));
                    }
                    candidates.add(TextSegment.from(text, metadata));
                }
            }

            if (candidates.isEmpty()) {
                return "";
            }

            // Pseudo-Rerank：复用 Bi-Encoder 计算余弦相似度，从 Top-N 粗排中精选 Top-K
            List<Double> scores = scoringModel.scoreAll(candidates, queryText).content();

            var ranked = IntStream.range(0, candidates.size())
                    .mapToObj(i -> new Object() {
                        final TextSegment segment = candidates.get(i);
                        final double score = scores.get(i);
                    })
                    .sorted(Comparator.comparingDouble(o -> -o.score))
                    .limit(RERANK_TOP_K)
                    .toList();

            List<String> contextList = ranked.stream()
                    .map(item -> {
                        String text = item.segment.text();
                        String filename = item.segment.metadata().getString("filename");
                        return String.format("[来源: %s (Score: %.2f)] %s", filename, item.score, text);
                    })
                    .collect(Collectors.toList());

            return String.join("\n---\n", contextList);

        } catch (IOException e) {
            log.error("Elasticsearch 检索失败, query={}", queryText, e);
            return "";
        }
    }

    /**
     * 将一路检索结果的排名转换为 RRF 分数并累加到全局映射中。
     * 提取为独立方法消除 BM25/kNN 两路的重复代码。
     */
    @SuppressWarnings("rawtypes")
    private void accumulateRrfScores(List<Hit<Map>> hits,
            LinkedHashMap<String, Double> rrfScores,
            LinkedHashMap<String, Map<String, Object>> docSources) {
        for (int rank = 0; rank < hits.size(); rank++) {
            Hit<Map> hit = hits.get(rank);
            String docId = hit.id();
            double score = 1.0 / (RRF_RANK_CONSTANT + rank + 1);
            rrfScores.merge(docId, score, Double::sum);
            if (hit.source() != null) {
                docSources.putIfAbsent(docId, hit.source());
            }
        }
    }

    /**
     * 判断查询是否包含上下文代词（如"我""你""他"等）。
     * 含代词的查询高度依赖对话历史，其回答不具备可复用性，写入缓存会导致缓存污染，
     * 因此主动跳过语义缓存的读写。
     */
    private boolean isContextDependent(String query) {
        if (query == null) {
            return true;
        }
        String lowerQuery = query.toLowerCase();
        for (String keyword : CONTEXT_SENSITIVE_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                log.debug("检测到上下文敏感词 '{}', 跳过语义缓存", keyword);
                return true;
            }
        }
        return false;
    }

    /**
     * 持久化对话记录。独立 try-catch 兜底，防止持久化异常影响主流程返回。
     */
    private void insertChatHistory(String sessionId, String question, String answer, ModelType modelType) {
        try {
            Long currentUserId = userUtils.getCurrentUserId();
            ChatHistory chatHistory = new ChatHistory()
                    .setSession_id(sessionId)
                    .setUserId(currentUserId)
                    .setUser_query(question)
                    .setAi_answer(answer)
                    .setModel_type(modelType.getName())
                    .setCreate_time(LocalDateTime.now());
            chatHistoryMapper.insert(chatHistory);
        } catch (Exception e) {
            log.error("对话记录持久化失败, sessionId={}, question={}", sessionId, question, e);
        }
    }
}
