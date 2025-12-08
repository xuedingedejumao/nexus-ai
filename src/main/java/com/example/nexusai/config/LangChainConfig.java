package com.example.nexusai.config;

import com.example.nexusai.service.KnowledgeAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.deepseek.normal-model.model-name}")
    private String normalModelName;

    @Value("${langchain4j.deepseek.normal-model.max-retries}")
    private int normalMaxRetries;

    @Value("${langchain4j.deepseek.normal-model.timeout}")
    private int normalTimeout;

    @Value("${langchain4j.deepseek.reasoning-model.model-name}")
    private String reasoningModelName;

    @Value("${langchain4j.deepseek.reasoning-model.max-retries}")
    private int reasoningMaxRetries;

    @Value("${langchain4j.deepseek.reasoning-model.timeout}")
    private int reasoningTimeout;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
    }

    /**
     * 普通对话模型
     */
    @Bean(name = "normalChatModel")
    public ChatLanguageModel normalChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(normalModelName)
                .maxRetries(normalMaxRetries)
                .timeout(Duration.ofSeconds(normalTimeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 深度思考模型
     */
    @Bean(name = "reasoningChatModel")
    public ChatLanguageModel reasoningChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(reasoningModelName)
                .maxRetries(reasoningMaxRetries)
                .timeout(Duration.ofSeconds(reasoningTimeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean("normalAgent")
    public KnowledgeAgent normalAgent(@Qualifier("normalChatModel") ChatLanguageModel chatModel,
                                      ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(chatModel) // 装上计算器
                .chatMemoryProvider(chatMemoryProvider) // ✅ 装上记忆
                .build();
    }

    @Bean("reasoningAgent")
    public KnowledgeAgent reasoningAgent(@Qualifier("reasoningChatModel") ChatLanguageModel chatModel,
                                         ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(chatModel) // 装上计算器
                .chatMemoryProvider(chatMemoryProvider) // ✅ 装上记忆
                .build();
    }
}