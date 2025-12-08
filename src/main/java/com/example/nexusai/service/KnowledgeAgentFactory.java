package com.example.nexusai.service;

import com.example.nexusai.enums.ModelType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.spring.AiService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class KnowledgeAgentFactory {
    private final ChatLanguageModel normalChatModel;
    private final ChatLanguageModel reasoningChatModel;

    private final Map<ModelType, KnowledgeAgent> agentCache = new EnumMap<>(ModelType.class);

    public KnowledgeAgentFactory(
            @Qualifier("normalChatModel") ChatLanguageModel normalChatModel,
            @Qualifier("reasoningChatModel") ChatLanguageModel reasoningChatModel
    ){
        this.normalChatModel = normalChatModel;
        this.reasoningChatModel = reasoningChatModel;
    }

    @PostConstruct
    public void init(){
        agentCache.put(ModelType.NORMAL, buildAgent(normalChatModel));
        agentCache.put(ModelType.REASONING, buildAgent(reasoningChatModel));
    }

    private KnowledgeAgent buildAgent(ChatLanguageModel chatModel){
        return AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    public KnowledgeAgent getAgent(ModelType modelType){
        return agentCache.getOrDefault(modelType, agentCache.get(ModelType.NORMAL));
    }

    public KnowledgeAgent getAgent(String modelCode){
        return getAgent(ModelType.fromCode(modelCode));
    }

}
