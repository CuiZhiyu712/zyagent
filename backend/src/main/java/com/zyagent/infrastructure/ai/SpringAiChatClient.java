package com.zyagent.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class SpringAiChatClient implements AiChatClient {
    private final ChatClient chatClient;
    private final ToolCallback[] toolCallbacks;

    SpringAiChatClient(ChatClient.Builder builder, ObjectProvider<ToolCallback[]> toolCallbacks) {
        this.chatClient = builder.build();
        this.toolCallbacks = toolCallbacks.getIfAvailable(() -> new ToolCallback[0]);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String content = chatClient.prompt()
            .system(systemPrompt == null ? "" : systemPrompt)
            .user(userPrompt == null ? "" : userPrompt)
            .toolCallbacks(toolCallbacks)
            .call()
            .content();
        return content == null ? "" : content;
    }

    @Override
    public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
        chatClient.prompt()
            .system(systemPrompt == null ? "" : systemPrompt)
            .user(userPrompt == null ? "" : userPrompt)
            .toolCallbacks(toolCallbacks)
            .stream()
            .content()
            .toIterable()
            .forEach(token -> {
                if (token != null && !token.isBlank()) {
                    handler.onToken(token);
                }
            });
    }
}
