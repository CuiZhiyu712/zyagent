package com.zyagent.agent;

import com.zyagent.storage.ChatMessageView;

import java.util.List;

public class ChatMemoryContext {
    private final int maxMessages;

    public ChatMemoryContext(int maxMessages) {
        this.maxMessages = Math.max(maxMessages, 0);
    }

    public String render(List<ChatMessageView> messages) {
        if (messages == null || messages.isEmpty() || maxMessages == 0) {
            return "";
        }
        int start = Math.max(0, messages.size() - maxMessages);
        List<ChatMessageView> recent = messages.subList(start, messages.size());
        StringBuilder builder = new StringBuilder("最近对话上下文：\n");
        for (ChatMessageView message : recent) {
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
