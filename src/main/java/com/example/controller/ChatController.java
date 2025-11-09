package com.example.controller;

import com.example.domain.Chat;
import com.example.domain.Message;
import com.example.service.ChatService;
import com.example.service.CurrentUserService;
import com.example.service.RagService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {
    private record CreateChatReq(@NotBlank String title) {}
    private record SendReq(@NotBlank String content) {}

    private final ChatService chatService;
    private final CurrentUserService currentUserService;
    private final RagService ragService;
    private final ExecutorService executorService;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    
    /**
     * 去除文件名中的UUID前缀
     * 例如: "a78c2d2a-289e-4062-8ff9-0751eb348cfc_Track_B_Example_QA.pdf" -> "Track_B_Example_QA.pdf"
     */
    private String removeUuidPrefix(String filename) {
        if (filename == null) return null;
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < filename.length() - 1) {
            String prefix = filename.substring(0, underscoreIndex);
            if (prefix.contains("-")) {
                return filename.substring(underscoreIndex + 1);
            }
        }
        return filename;
    }

    public ChatController(ChatService chatService, CurrentUserService currentUserService, RagService ragService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
        this.ragService = ragService;
        this.executorService = Executors.newCachedThreadPool();
    }

    @PostMapping("/create")
    public ResponseEntity<Chat> create(@RequestBody CreateChatReq req, Authentication auth) {
        Long userId = currentUserService.requireUserIdByUsername(auth.getName());
        log.info("[ChatController] create chat, user={}, title={}", auth.getName(), req.title());
        Chat chat = chatService.createChat(userId, req.title());
        log.info("[ChatController] chat created, chatId={}", chat.getId());
        return ResponseEntity.ok(chat);
    }

    @GetMapping("/list")
    public ResponseEntity<List<Chat>> list(Authentication auth) {
        Long userId = currentUserService.requireUserIdByUsername(auth.getName());
        List<Chat> chats = chatService.listChats(userId);
        log.info("[ChatController] list chats, user={}, count={}", auth.getName(), chats.size());
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/{chatId}/history")
    public ResponseEntity<List<Message>> history(@PathVariable("chatId") Long chatId) {
        List<Message> history = chatService.history(chatId);
        log.info("[ChatController] history, chatId={}, messages={}", chatId, history.size());
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{chatId}/send")
    public ResponseEntity<List<Message>> send(@PathVariable("chatId") Long chatId, @RequestBody SendReq req) {
        log.info("[ChatController] send, chatId={}, contentLen={}", chatId, req.content() == null ? 0 : req.content().length());
        chatService.userSend(chatId, req.content());
        chatService.aiReply(chatId, req.content());
        List<Message> all = chatService.history(chatId);
        log.info("[ChatController] send done, chatId={}, totalMessages={}", chatId, all.size());
        return ResponseEntity.ok(all);
    }

    @PostMapping(value = "/{chatId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("chatId") Long chatId, @RequestBody SendReq req) {
        log.info("[ChatController] stream, chatId={}, contentLen={}", chatId, req.content() == null ? 0 : req.content().length());
        
        // 先保存用户消息
        chatService.userSend(chatId, req.content());
        
        // 获取RAG引用列表
        Set<String> references = ragService.getRagReferences(req.content());
        
        // 创建 SSE emitter，设置超时时间为 5 分钟
        SseEmitter emitter = new SseEmitter(300000L);
        StringBuilder fullResponse = new StringBuilder();
        
        // 创建流式响应处理器
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                try {
                    fullResponse.append(token);
                    // 发送每个 token 到前端
                    // 确保每个事件都有正确的格式
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name("token")
                        .data(token != null ? token : "");
                    emitter.send(event);
                    log.debug("[ChatController] Sent token, len={}", token != null ? token.length() : 0);
                } catch (IOException e) {
                    log.error("[ChatController] Failed to send token", e);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("[ChatController] Failed to complete emitter with error", ex);
                    }
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    // 保存完整的 AI 回复到数据库
                    String answer = fullResponse.toString();
                    // 如果 fullResponse 为空，从 response 中获取
                    if (answer.isEmpty() && response != null && response.content() != null) {
                        answer = response.content().text();
                    }
                    
                    // 添加引用列表（如果有）
                    if (references != null && !references.isEmpty()) {
                        StringBuilder answerWithRefs = new StringBuilder(answer);
                        answerWithRefs.append("\n\n---\n\n");
                        answerWithRefs.append("**📚 参考文档：**\n\n");
                        for (String ref : references) {
                            // 去除UUID前缀，只显示原始文件名
                            String displayName = removeUuidPrefix(ref);
                            answerWithRefs.append("- ").append(displayName).append("\n");
                        }
                        answer = answerWithRefs.toString();
                    }
                    
                    chatService.aiReplySave(chatId, answer);
                    // 发送完成事件
                    emitter.send(SseEmitter.event()
                        .name("done")
                        .data(""));
                    emitter.complete();
                    log.info("[ChatController] stream done, chatId={}, answerLen={}, references={}", 
                             chatId, answer.length(), references.size());
                } catch (IOException e) {
                    log.error("[ChatController] Failed to send completion", e);
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("[ChatController] Stream error", error);
                emitter.completeWithError(error);
            }
        };
        
        // 设置完成和错误回调
        emitter.onCompletion(() -> {
            log.debug("[ChatController] SSE emitter completed for chatId={}", chatId);
        });
        
        emitter.onError((ex) -> {
            log.error("[ChatController] SSE emitter error for chatId={}", chatId, ex);
        });
        
        emitter.onTimeout(() -> {
            log.warn("[ChatController] SSE emitter timeout for chatId={}", chatId);
            emitter.complete();
        });
        
        // 异步执行流式生成（带历史上下文）
        CompletableFuture.runAsync(() -> {
            try {
                ragService.chatWithRagStreaming(chatId, req.content(), handler);
            } catch (Exception e) {
                log.error("[ChatController] Failed to start streaming", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("[ChatController] Failed to complete emitter with error", ex);
                }
            }
        }, executorService);
        
        return emitter;
    }
}


