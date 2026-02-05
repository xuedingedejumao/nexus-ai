package com.example.nexusai.service;

import com.example.nexusai.enums.ModelType;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class KnowledgeAgentFactory {
    private final ChatLanguageModel normalChatModel;
    private final ChatLanguageModel reasoningChatModel;
    private final StreamingChatLanguageModel normalStreamingChatModel;
    private final StreamingChatLanguageModel reasoningStreamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;

    private final Map<ModelType, KnowledgeAgent> agentCache = new EnumMap<>(ModelType.class);
    private final Map<ModelType, StreamKnowledgeAgent> streamAgentCache = new EnumMap<>(ModelType.class);

    public KnowledgeAgentFactory(
            @Qualifier("normalChatModel") ChatLanguageModel normalChatModel,
            @Qualifier("reasoningChatModel") ChatLanguageModel reasoningChatModel,
            @Qualifier("normalStreamingChatModel") StreamingChatLanguageModel normalStreamingChatModel,
            @Qualifier("reasoningStreamingChatModel") StreamingChatLanguageModel reasoningStreamingChatModel,
            ChatMemoryProvider chatMemoryProvider
    ){
        this.normalChatModel = normalChatModel;
        this.reasoningChatModel = reasoningChatModel;
        this.normalStreamingChatModel = normalStreamingChatModel;
        this.reasoningStreamingChatModel = reasoningStreamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    @PostConstruct
    public void init(){
        agentCache.put(ModelType.NORMAL, buildAgent(normalChatModel));
        agentCache.put(ModelType.REASONING, buildAgent(reasoningChatModel));

        streamAgentCache.put(ModelType.NORMAL, buildStreamAgent(normalStreamingChatModel));
        streamAgentCache.put(ModelType.REASONING, buildStreamAgent(reasoningStreamingChatModel));
    }

    private KnowledgeAgent buildAgent(ChatLanguageModel chatModel){
        return AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    private StreamKnowledgeAgent buildStreamAgent(StreamingChatLanguageModel streamingChatModel){
        return AiServices.builder(StreamKnowledgeAgent.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    public KnowledgeAgent getAgent(ModelType modelType){
        return agentCache.getOrDefault(modelType, agentCache.get(ModelType.NORMAL));
    }

    public StreamKnowledgeAgent getStreamAgent(ModelType modelType){
        return streamAgentCache.getOrDefault(modelType, streamAgentCache.get(ModelType.NORMAL));
    }

    public KnowledgeAgent getAgent(String modelCode){
        return getAgent(ModelType.fromCode(modelCode));
    }

}
