package com.example.nexusai.service;

import com.example.nexusai.entity.ChatHistory;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.mapper.ChatHistoryMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeAgentFactory agentFactory;
    private final ChatHistoryMapper chatHistoryMapper;
    private final StreamKnowledgeAgent streamKnowledgeAgent;

    public void ingest(String content, String filename){
        Document document = Document.from(content, Metadata.from("filename", filename));

        List<TextSegment> segments = DocumentSplitters.recursive(500,100).split(document);
        log.info("文档分割完成，段落数：{}", segments.size());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        embeddingStore.addAll(embeddings, segments);

        log.info("文档入库完成");
    }

    /**
     *
     * @param query 用户问题
     * @param modelType 模型类型
     * @param sessionId 会话ID
     * @return 答案
     */
    public String chat(String query, ModelType modelType, String sessionId){
        try{
            log.info("收到用户[{}]问题：{}, 使用模型：{}", sessionId, query, modelType.getName());
            String context = retrieveContext(query);

            KnowledgeAgent agent = agentFactory.getAgent(modelType);
            long startTime = System.currentTimeMillis();
            String answer = agent.answer(sessionId, query, context);
            long duration = System.currentTimeMillis() - startTime;
            log.info("模型回答完成，耗时：{} ms", duration);

            insertChatHistory(sessionId, query, answer, modelType);
            return answer;
        }catch (Exception e){
            log.error("查询失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    public SseEmitter streamChat(String query, String sessionId){
        String context = retrieveContext(query);

        SseEmitter sseEmitter = new SseEmitter(5*60*1000L);

        TokenStream tokenStream = streamKnowledgeAgent.chat(sessionId, query, context);

        tokenStream
                .onNext(token -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                }).onComplete(token -> {
                    sseEmitter.complete();
                })
                .onError(sseEmitter::completeWithError)
                .start();
        return sseEmitter;
    }


    /**
     * 根据用户查询检索相关上下文
     * @param query 用户查询
     * @return 检索结果
     */
    private String retrieveContext(String query){
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.6)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<String> contextList = result.matches().stream()
                .map(match -> {
                    String text = match.embedded().text();
                    String source = match.embedded().metadata().get("filename");
                    return String.format("[来源：%s] %s", source, text);
                })
                .collect(Collectors.toList());

        if(contextList.isEmpty()){
            return "";
        }
        return String.join("\n---\n", contextList);
    }

    private void insertChatHistory(String sessionId, String question, String answer, ModelType modelType){
        ChatHistory chatHistory = new ChatHistory()
                .setSession_id(sessionId)
                .setUser_query(question)
                .setAi_answer(answer)
                .setModel_type(modelType.getName())
                .setCreate_time(java.time.LocalDateTime.now());
        chatHistoryMapper.insert(chatHistory);
    }
}
