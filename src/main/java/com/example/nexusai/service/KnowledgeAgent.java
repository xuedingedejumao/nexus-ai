package com.example.nexusai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;


public interface KnowledgeAgent {

    @SystemMessage("""
            你是一个专业的企业知识库助手。
                       \s
                        你的回答逻辑如下：
                        1. 首先参考【已知信息】中的内容。
                        2. 如果【已知信息】为空或与问题无关，请尝试结合【对话历史】进行推理回答。
                        3. 如果依然无法回答，再告知用户“未找到相关信息”。
                       \s
                        【已知信息】：
                        {{context}}
            """)
    String answer(@MemoryId Object sessionId, @UserMessage String question, @V("context") String context);
}
