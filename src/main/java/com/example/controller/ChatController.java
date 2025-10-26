package com.example.controller;

import com.example.domain.Chat;
import com.example.domain.Message;
import com.example.service.ChatService;
import com.example.service.CurrentUserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public ChatController(ChatService chatService, CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
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
}


