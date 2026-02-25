package com.example.nexusai.controller;

import com.example.nexusai.dto.ChatResponse;
import com.example.nexusai.enums.ModelType;
import com.example.nexusai.service.RagService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ChatController {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final String DEFAULT_MODEL_TYPE = "NORMAL";

    private final RagService ragService;

    @GetMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestParam @NotBlank(message = "查询内容不能为空") String query,
            @RequestParam(defaultValue = DEFAULT_MODEL_TYPE) String modelType,
            @RequestParam(required = false) String sessionId,
            Authentication authentication) {
        try {
            String username = resolveUsername(authentication);
            String safeSessionId = resolveSessionId(sessionId);
            String distinctId = username + ":" + safeSessionId;
            ModelType type = ModelType.valueOf(modelType.toUpperCase());

            log.info("用户 [{}] 发起对话, 模型: {}, session: {}", username, type, safeSessionId);

            long startTime = System.currentTimeMillis();
            String answer = ragService.chat(query, type, distinctId);
            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(ChatResponse.success(answer, type.name(), type.name(), duration));

        } catch (IllegalArgumentException e) {
            log.warn("无效的模型类型: {}", modelType);
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("无效的模型类型: " + modelType + "，请使用 NORMAL 或 REASONING"));
        } catch (Exception e) {
            log.error("对话请求处理失败, query={}", query, e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("查询失败: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam @NotBlank(message = "查询内容不能为空") String query,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = DEFAULT_MODEL_TYPE) String modelType,
            Authentication authentication) {
        String username = resolveUsername(authentication);
        String safeSessionId = resolveSessionId(sessionId);
        String distinctId = username + ":" + safeSessionId;
        ModelType type = ModelType.valueOf(modelType.toUpperCase());

        log.info("用户 [{}] 发起流式对话, session: {}", username, safeSessionId);
        return ragService.streamChat(query, type, distinctId);
    }

    private String resolveUsername(Authentication authentication) {
        return (authentication != null) ? authentication.getName() : "anonymous";
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.trim().isEmpty()) ? DEFAULT_SESSION_ID : sessionId;
    }
}