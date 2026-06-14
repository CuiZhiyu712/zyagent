package com.zyagent.ai;

interface AiChatClient {
    String complete(String systemPrompt, String userPrompt);

    void stream(String systemPrompt, String userPrompt, TokenHandler handler);
}
