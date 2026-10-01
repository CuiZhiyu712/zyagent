package com.zyagent.agent;

import com.zyagent.config.ZyagentProperties;
import com.zyagent.storage.ChatRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatMemoryService {
    private final ChatRepository chatRepository;
    private final ChatMemoryContext context;

    public ChatMemoryService(ObjectProvider<ChatRepository> chatRepository, ZyagentProperties properties) {
        this.chatRepository = chatRepository.getIfAvailable();
        int maxMessages = properties.chat() == null || properties.chat().memory() == null
            ? 6
            : properties.chat().memory().maxMessages();
        this.context = new ChatMemoryContext(maxMessages);
    }

    public String render(String sessionId) {
        return snapshot(sessionId).summary();
    }

    public ChatMemorySnapshot snapshot(String sessionId) {
        if (chatRepository == null || sessionId == null || sessionId.isBlank()) {
            return ChatMemorySnapshot.empty();
        }
        try {
            return context.snapshot(chatRepository.listMessages(sessionId));
        } catch (RuntimeException ex) {
            return ChatMemorySnapshot.empty();
        }
    }
}
