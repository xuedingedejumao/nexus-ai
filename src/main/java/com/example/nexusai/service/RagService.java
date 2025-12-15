package com.example.nexusai.service;

import com.example.nexusai.entity.ChatHistory;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.mapper.ChatHistoryMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public void ingest(String text){
        log.info("开始处理文档入库，长度：{}", text.length());
        String cleanedText = cleanText(text);
        TextSegment segment = TextSegment.from(cleanedText);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
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

    private void saveHistory(){

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
                .minScore(0.7)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<String> contextList = result.matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());

        if(contextList.isEmpty()){
            log.info("知识库中未找到相关信息");
            return "知识库中未找到相关信息";
        }
        return String.join("\n---\n", contextList);
    }

    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text
                // 移除 Markdown 粗体
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                // 移除斜体
                .replaceAll("\\*(.+?)\\*", "$1")
                // 移除代码标记
                .replaceAll("`(.+?)`", "$1")
                // 移除标题标记
                .replaceAll("^#{1,6}\\s+", "")
                // 清理多余空格
                .replaceAll("\\s+", " ")
                .trim();
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
