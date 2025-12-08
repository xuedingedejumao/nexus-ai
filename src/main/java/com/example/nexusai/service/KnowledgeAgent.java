package com.example.nexusai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;


public interface KnowledgeAgent {

    @SystemMessage("""
            你是一个专业的企业知识库助手。
            请严格基于传入的【已知信息】回答用户问题。
            如果信息不足，请直接回答"知识库中未找到相关信息"，不要编造。
            
            【已知信息】：
            {{context}}
            """)
    String answer(@MemoryId Object sessionId, @UserMessage String question, @V("context") String context);
}
