package com.example.service;

import com.example.domain.Chat;
import com.example.domain.Message;
import com.example.mapper.ChatMapper;
import com.example.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ChatService {
    private final ChatMapper chatMapper;
    private final MessageMapper messageMapper;
    private final RagService ragService;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public ChatService(ChatMapper chatMapper, MessageMapper messageMapper, RagService ragService) {
        this.chatMapper = chatMapper;
        this.messageMapper = messageMapper;
        this.ragService = ragService;
    }

    public Chat createChat(Long userId, String title) {
        Chat chat = new Chat();
        chat.setUserId(userId);
        chat.setTitle(title);
        chatMapper.insert(chat);
        log.info("[ChatService] chat created, userId={}, chatId={}, title={}", userId, chat.getId(), title);
        return chat;
    }

    public List<Chat> listChats(Long userId) {
        List<Chat> list = chatMapper.listByUser(userId);
        log.info("[ChatService] list chats, userId={}, count={}", userId, list.size());
        return list;
    }

    public List<Message> history(Long chatId) {
        List<Message> list = messageMapper.listByChat(chatId);
        log.debug("[ChatService] history loaded, chatId={}, messages={}", chatId, list.size());
        return list;
    }

    public Message userSend(Long chatId, String content) {
        Message m = new Message();
        m.setChatId(chatId);
        m.setRole("user");
        m.setContent(content);
        messageMapper.insert(m);
        log.info("[ChatService] user message saved, chatId={}, messageId={}, len={}", chatId, m.getId(), content == null ? 0 : content.length());
        return m;
    }

    public Message aiReply(Long chatId, String userContent) {
        long t0 = System.currentTimeMillis();
        String answer = ragService.chatWithRag(userContent);
        long dt = System.currentTimeMillis() - t0;
        Message m = new Message();
        m.setChatId(chatId);
        m.setRole("assistant");
        m.setContent(answer);
        messageMapper.insert(m);
        log.info("[ChatService] ai reply saved, chatId={}, messageId={}, costMs={}, answerLen={}", chatId, m.getId(), dt, answer == null ? 0 : answer.length());
        return m;
    }
}


