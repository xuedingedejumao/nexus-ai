package com.example.nexusai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private boolean success;
    private String answer;
    private String modelType;
    private String modelName;
    private Long duration;  // 响应时间（毫秒）
    private String error;

    public static ChatResponse success(String answer, String modelType, String modelName, Long duration) {
        return ChatResponse.builder()
                .success(true)
                .answer(answer)
                .modelType(modelType)
                .modelName(modelName)
                .duration(duration)
                .build();
    }

    public static ChatResponse error(String error) {
        return ChatResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
