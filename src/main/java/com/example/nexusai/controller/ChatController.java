package com.example.nexusai.controller;

import com.example.nexusai.dto.ChatResponse;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RagService ragService;

    // ================== 普通对话接口 ==================
    @GetMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestParam String query,
            @RequestParam(defaultValue = "NORMAL") String modelType,
            @RequestParam(required = false) String sessionId,
            Authentication authentication // 👈 1. 注入认证信息
    ) {
        try {
            // 2. 获取当前用户名
            String username = (authentication != null) ? authentication.getName() : "anonymous";

            // 3. 修复空指针：如果没传 sessionId，给个默认值 "default"
            String safeSessionId = (sessionId == null || sessionId.trim().isEmpty()) ? "default" : sessionId;

            // 4. 构建唯一会话ID (用户隔离关键点: "jackie:default")
            String distinctId = username + ":" + safeSessionId;

            // 将字符串转换为枚举
            ModelType type = ModelType.valueOf(modelType.toUpperCase());

            log.info("用户 [{}] 发起对话, 模型: {}, Session: {}", username, type, safeSessionId);

            long startTime = System.currentTimeMillis();
            // 5. 传给 Service 的是 distinctId
            String answer = ragService.chat(query, type, distinctId);
            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(ChatResponse.success(
                    answer,
                    type.name(),
                    type.name(), // 这里假设你的 ChatResponse 需要两个 modelName
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

    // ================== 流式对话接口 ==================
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "NORMAL") String modelType,
            Authentication authentication // 👈 1. 注入认证信息
    ){
        // 获取用户名
        String username = (authentication != null) ? authentication.getName() : "anonymous";

        // 判空逻辑
        String safeSessionId = (sessionId == null || sessionId.trim().isEmpty()) ? "default" : sessionId;

        // 构建隔离ID
        String distinctId = username + ":" + safeSessionId;

        ModelType type = ModelType.valueOf(modelType.toUpperCase());

        log.info("用户 [{}] 发起流式对话, Session: {}", username, safeSessionId);

        // 传给 Service 的是 distinctId
        return ragService.streamChat(query, type, distinctId);
    }
}