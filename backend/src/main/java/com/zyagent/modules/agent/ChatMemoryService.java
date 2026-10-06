package com.zyagent.modules.agent;

import com.zyagent.infrastructure.config.ZyagentProperties;
import com.zyagent.infrastructure.storage.ChatRepository;
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

    /** 按 Skill 作用域渲染：切换任务后不再把上一个 Skill 的输出带进新任务。 */
    public String renderForSkill(String sessionId, String skillId) {
        if (chatRepository == null || sessionId == null || sessionId.isBlank()) {
            return "";
        }
        try {
            return context.renderForSkill(chatRepository.listMessages(sessionId), skillId);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public ChatMemorySnapshot snapshot(String sessionId) {
        if (chatRepository == null || sessionId == null || sessionId.isBlank()) {
            return ChatMemorySnapshot.empty();
        }
        try {
            ChatMemorySnapshot fromMessages = context.snapshot(chatRepository.listMessages(sessionId));
            ChatTaskState taskState = chatRepository.findTaskState(sessionId).orElse(ChatTaskState.empty());
            // 任务状态是 Active Skill 的权威来源；助手消息里的 Skill 只在任务状态缺失时兜底。
            String activeSkill = taskState.hasActiveSkill() ? taskState.activeSkill() : fromMessages.activeSkillId();
            return new ChatMemorySnapshot(fromMessages.recentMessageCount(), fromMessages.summary(),
                activeSkill, taskState.currentDay());
        } catch (RuntimeException ex) {
            return ChatMemorySnapshot.empty();
        }
    }

    /**
     * 记录本轮路由结果到会话任务状态。
     *
     * <p>切换 Skill 视为新任务，天数按本轮重置；同一任务内保留已到达的天数进度。
     */
    public void saveTaskState(String sessionId, String taskType, String activeSkill, int dayInMessage) {
        if (chatRepository == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            ChatTaskState existing = chatRepository.findTaskState(sessionId).orElse(ChatTaskState.empty());
            int day = activeSkill != null && activeSkill.equals(existing.activeSkill())
                ? Math.max(existing.currentDay(), dayInMessage)
                : dayInMessage;
            chatRepository.saveTaskState(sessionId, new ChatTaskState(taskType, activeSkill, Math.max(0, day)));
        } catch (RuntimeException ignored) {
            // 无数据库演示模式：任务状态退化为按聊天记录推导
        }
    }
}
