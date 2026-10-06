package com.zyagent.infrastructure.ai;

interface AiChatClient {
    String complete(String systemPrompt, String userPrompt);

    void stream(String systemPrompt, String userPrompt, TokenHandler handler);
}
