package com.zyagent.modules.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.infrastructure.storage.ChatMessageView;

import java.util.List;

/**
 * 会话记忆的组装与 Skill 作用域隔离。
 *
 * <p>两个职责：
 * <ol>
 *   <li>从最近消息里得出 **Active Skill**（最近一条助手消息真实使用的 Skill），作为路由的会话级状态；</li>
 *   <li>按 Skill 作用域渲染历史：只保留当前任务那次对话以来的消息，
 *       避免切换任务后把上一个 Skill 的长输出继续喂给新 Skill（上下文串线）。</li>
 * </ol>
 */
public class ChatMemoryContext {
    private final int maxMessages;
    private final ObjectMapper objectMapper;

    public ChatMemoryContext(int maxMessages) {
        this(maxMessages, new ObjectMapper());
    }

    public ChatMemoryContext(int maxMessages, ObjectMapper objectMapper) {
        this.maxMessages = Math.max(maxMessages, 0);
        this.objectMapper = objectMapper;
    }

    public String render(List<ChatMessageView> messages) {
        return snapshot(messages).summary();
    }

    public ChatMemorySnapshot snapshot(List<ChatMessageView> messages) {
        if (messages == null || messages.isEmpty() || maxMessages == 0) {
            return ChatMemorySnapshot.empty();
        }
        List<ChatMessageView> recent = recent(messages);
        return new ChatMemorySnapshot(recent.size(), renderLines(recent), lastAssistantSkill(recent));
    }

    /** 只渲染当前 Skill 那次任务以来的消息；没有历史或未识别出 Skill 时退回最近 N 条。 */
    public String renderForSkill(List<ChatMessageView> messages, String skillId) {
        if (messages == null || messages.isEmpty() || maxMessages == 0 || skillId == null || skillId.isBlank()) {
            return render(messages);
        }
        int start = skillRunStart(messages, skillId);
        List<ChatMessageView> scoped = messages.subList(start, messages.size());
        List<ChatMessageView> limited = scoped.size() > maxMessages
            ? scoped.subList(scoped.size() - maxMessages, scoped.size())
            : scoped;
        return renderLines(limited);
    }

    /** 最近一条助手消息使用的 Skill；读取助手消息 payload 里的 {@code skill} 字段。 */
    public String lastAssistantSkill(List<ChatMessageView> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageView message = messages.get(i);
            if ("assistant".equalsIgnoreCase(message.role())) {
                String skill = skillOf(message);
                if (skill != null && !skill.isBlank()) {
                    return skill;
                }
            }
        }
        return null;
    }

    /**
     * 当前 Skill 那次任务的起点：最后一条"属于别的 Skill"的助手消息之后。
     *
     * <p>例如 resume → resume → interview，若当前 Skill 是 interview，
     * 起点落在面试那次任务的用户消息上，简历任务的输出不会进入上下文。
     */
    private int skillRunStart(List<ChatMessageView> messages, String skillId) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageView message = messages.get(i);
            if (!"assistant".equalsIgnoreCase(message.role())) {
                continue;
            }
            String skill = skillOf(message);
            if (skill != null && !skill.isBlank() && !skill.equals(skillId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private String skillOf(ChatMessageView message) {
        String payload = message.referencesJson();
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.path("skill").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<ChatMessageView> recent(List<ChatMessageView> messages) {
        int start = Math.max(0, messages.size() - maxMessages);
        return messages.subList(start, messages.size());
    }

    private String renderLines(List<ChatMessageView> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("最近对话上下文：\n");
        for (ChatMessageView message : messages) {
            builder.append(label(message.role()))
                .append("：")
                .append(message.content())
                .append('\n');
        }
        return builder.toString().strip();
    }

    private String label(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "助手";
        }
        if ("user".equalsIgnoreCase(role)) {
            return "用户";
        }
        return role == null ? "消息" : role;
    }
}
