package com.zyagent.ai;

interface AiChatClient {
    String complete(String systemPrompt, String userPrompt);

    default String completeWithoutTools(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt);
    }

    void stream(String systemPrompt, String userPrompt, TokenHandler handler);
}
