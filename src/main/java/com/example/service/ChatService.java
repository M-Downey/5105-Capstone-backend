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
    
    /**
     * 去除文件名中的UUID前缀
     * 例如: "a78c2d2a-289e-4062-8ff9-0751eb348cfc_Track_B_Example_QA.pdf" -> "Track_B_Example_QA.pdf"
     */
    private String removeUuidPrefix(String filename) {
        if (filename == null) return null;
        // 查找第一个下划线的位置
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < filename.length() - 1) {
            // 检查下划线前面是否是UUID格式（包含连字符）
            String prefix = filename.substring(0, underscoreIndex);
            if (prefix.contains("-")) {
                return filename.substring(underscoreIndex + 1);
            }
        }
        return filename;
    }

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
        
        // 获取RAG引用列表
        java.util.Set<String> references = ragService.getRagReferences(userContent);
        
        // 使用带历史上下文的版本
        String answer = ragService.chatWithRag(chatId, userContent);
        
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
        
        long dt = System.currentTimeMillis() - t0;
        Message m = new Message();
        m.setChatId(chatId);
        m.setRole("assistant");
        m.setContent(answer);
        messageMapper.insert(m);
        log.info("[ChatService] ai reply saved, chatId={}, messageId={}, costMs={}, answerLen={}, references={}", 
                 chatId, m.getId(), dt, answer == null ? 0 : answer.length(), references.size());
        return m;
    }

    public Message aiReplySave(Long chatId, String answer) {
        Message m = new Message();
        m.setChatId(chatId);
        m.setRole("assistant");
        m.setContent(answer);
        messageMapper.insert(m);
        log.info("[ChatService] ai reply saved, chatId={}, messageId={}, answerLen={}", chatId, m.getId(), answer == null ? 0 : answer.length());
        return m;
    }
}


