package com.example.nexusai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 模型配置：基于 OpenAI 兼容协议接入 DeepSeek API。
 * <p>
 * 同时注册普通模型和推理模型的同步/流式共 4 个 Bean，由 KnowledgeAgentFactory 按 ModelType 分发。
 * 超时、重试等参数从配置文件注入，便于不同环境差异化调优。
 */
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

    @Bean(name = "normalStreamingChatModel")
    public StreamingChatLanguageModel normalStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(normalModelName)
                .timeout(Duration.ofSeconds(normalTimeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean(name = "reasoningStreamingChatModel")
    public StreamingChatLanguageModel reasoningStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(reasoningModelName)
                .timeout(Duration.ofSeconds(reasoningTimeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}