package com.example.nexusai.service;

import dev.langchain4j.service.*;

public interface StreamKnowledgeAgent {
    @SystemMessage("""
            你是一个专业的企业知识库助手。
            请优先根据【已知信息】回答。
            
            【已知信息】：
            {{context}}
            """)
    TokenStream chat(@MemoryId Object sessionId, @UserMessage String question, @V("context") String context);
}
