package com.example.nexusai.dto;

import com.example.nexusai.enums.ModelType;
import lombok.Data;

@Data
public class ChatRequest {

    private String query;

    // 默认使用普通模型
    private ModelType modelType = ModelType.NORMAL;
}