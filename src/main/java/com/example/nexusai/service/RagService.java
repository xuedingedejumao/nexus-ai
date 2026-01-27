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
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
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
    private final StreamKnowledgeAgent streamKnowledgeAgent;

    private final SemanticCacheService semanticCacheService;
    private final UserUtils userUtils;

    // ... (ingest method unchanged)

    private String retrieveContext(String query){
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20) 
                .minScore(0.5) 
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        
        if(result.matches().isEmpty()){
            return "";
        }

        List<TextSegment> candidates = result.matches().stream()
                .map(dev.langchain4j.store.embedding.EmbeddingMatch::embedded)
                .collect(Collectors.toList());

        List<Double> scores = scoringModel.scoreAll(candidates, query);
        
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

    /**
     * 判断问题是否依赖上下文（敏感词检测）
     * 如果包含 "我"、"你"、"谁" 等代词，通常意味着答案是动态的，不适合缓存。
     */
    private boolean isContextDependent(String query) {
        if (query == null) return true;
        String q = query.toLowerCase();

        // 简单粗暴但有效的关键词列表
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