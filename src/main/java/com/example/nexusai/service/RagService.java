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
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;
    private final KnowledgeAgentFactory agentFactory;
    private final ChatHistoryMapper chatHistoryMapper;
    
    private final SemanticCacheService semanticCacheService;
    private final UserUtils userUtils;

    public void ingest(String content, String filename){
        Document document = Document.from(content, Metadata.from("filename", filename));

        List<TextSegment> segments = DocumentSplitters.recursive(500,100).split(document);
        log.info("文档分割完成，段落数：{}", segments.size());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        embeddingStore.addAll(embeddings, segments);

        log.info("文档入库完成");
    }

    @RateLimiter(name = "chatApi")
    public String chat(String query, ModelType modelType, String sessionId){
        try{
            log.info("收到用户[{}]问题：{}, 使用模型：{}", sessionId, query, modelType.getName());

            // --- 1. 缓存层：查询 ---
            if (!isContextDependent(query)) {
                Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
                if(cachedAnswer.isPresent()){
                    String answer = cachedAnswer.get();
                    log.info("🎯 语义缓存命中！直接返回结果。");
                    insertChatHistory(sessionId, query, answer, modelType);
                    return answer;
                }
            }

            // --- 2. 业务层：RAG 检索与生成 ---
            String context = retrieveContext(query);
            KnowledgeAgent agent = agentFactory.getAgent(modelType);

            long startTime = System.currentTimeMillis();
            String answer = agent.answer(sessionId, query, context);
            long duration = System.currentTimeMillis() - startTime;
            log.info("模型回答完成，耗时：{} ms", duration);

            // --- 3. 缓存层：回写 ---
            if (!isContextDependent(query)) {
                semanticCacheService.setCachedAnswer(query, answer);
            }

            // --- 4. 持久化 ---
            insertChatHistory(sessionId, query, answer, modelType);
            return answer;

        }catch (Exception e){
            log.error("查询失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 流式对话接口
     */
    public SseEmitter streamChat(String query, ModelType modelType, String sessionId){
        SseEmitter sseEmitter = new SseEmitter(5*60*1000L);

        // --- 1. 缓存层：查询 ---
        if (!isContextDependent(query)) {
            Optional<String> cachedAnswer = semanticCacheService.getCachedAnswer(query);
            if(cachedAnswer.isPresent()){
                String answer = cachedAnswer.get();
                log.info("🎯 语义缓存命中！直接返回结果。");
                insertChatHistory(sessionId, query, answer, modelType);
                
                // 模拟流式输出缓存内容
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(answer));
                        sseEmitter.send(SseEmitter.event().data("[DONE]")); // 前端结束标志
                        sseEmitter.complete();
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                });
                return sseEmitter;
            }
        }

        // --- 2. 缓存未命中：执行正常流式逻辑 ---
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
                            log.info("流式输出结束，写入语义缓存...");
                            semanticCacheService.setCachedAnswer(query, fullAnswer);
                        }

                        log.info("流式输出结束，保存到数据库...");
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
     * 根据用户查询检索相关上下文 (Rerank Enhanced)
     */
    private String retrieveContext(String query){
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        // 1. 粗排 (Retrieve Top-20)
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20) 
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        
        if(result.matches().isEmpty()){
            return "";
        }

        List<TextSegment> candidates = result.matches().stream()
                .map(dev.langchain4j.store.embedding.EmbeddingMatch::embedded)
                .collect(Collectors.toList());

        // 2. 精排 (Rerank Top-20 -> Top-3)
        // 注意：scoreAll 返回的是 Response<List<Double>>，需要 .content()
        List<Double> scores = scoringModel.scoreAll(candidates, query).content();
        
        class ScoredSegment {
            TextSegment segment;
            Double score;
            ScoredSegment(TextSegment s, Double v) { segment = s; score = v; }
        }

        List<ScoredSegment> ranked = java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(i -> new ScoredSegment(candidates.get(i), scores.get(i)))
                .sorted(Comparator.comparingDouble((ScoredSegment s) -> s.score).reversed())
                .limit(3) 
                .toList();

        List<String> contextList = ranked.stream()
                .map(s -> {
                    String text = s.segment.text();
                    String source = s.segment.metadata().get("filename");
                    return String.format("[来源：%s (Score: %.2f)] %s", source, s.score, text);
                })
                .collect(Collectors.toList());

        return String.join("\n---\n", contextList);
    }

    private boolean isContextDependent(String query) {
        if (query == null) return true;
        String q = query.toLowerCase();
        String[] sensitiveKeywords = {
                "我", "你", "您", "谁", "它", "他", "她",
                "my", "i ", "you", "who", "this", "that"
        };
        for (String keyword : sensitiveKeywords) {
            if (q.contains(keyword)) {
                log.info("检测到上下文敏感词 '{}'，跳过语义缓存。", keyword);
                return true;
            }
        }
        return false;
    }

    private void insertChatHistory(String sessionId, String question, String answer, ModelType modelType){
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
