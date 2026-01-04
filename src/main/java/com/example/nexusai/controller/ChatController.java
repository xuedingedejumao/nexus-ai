package com.example.nexusai.controller;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.service.RagService;
import com.example.nexusai.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class ChatController {
    @Autowired
    private RagService ragService;

    @GetMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestParam String query,
            @RequestParam(defaultValue = "NORMAL") String modelType,
            @RequestParam(required = false) String sessionId) {
        try {
            String finalSessionId = sessionId.isEmpty()? "default user" : sessionId;

            // 将字符串转换为枚举
            ModelType type = ModelType.valueOf(modelType.toUpperCase());

            long startTime = System.currentTimeMillis();
            String answer = ragService.chat(query, type, finalSessionId);
            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(ChatResponse.success(
                    answer,
                    type.name(),
                    type.name(),
                    duration
            ));

        } catch (IllegalArgumentException e) {
            log.error("无效的模型类型: {}", modelType);
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("无效的模型类型: " + modelType + "，请使用 NORMAL 或 REASONING"));
        } catch (Exception e) {
            log.error("查询失败", e);
            return ResponseEntity.status(500)
                    .body(ChatResponse.error("查询失败: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId
    ){
        String finalSessionId = (sessionId == null || sessionId.isEmpty()) ? "default user" : sessionId;
        return ragService.streamChat(query, finalSessionId);
    }
}