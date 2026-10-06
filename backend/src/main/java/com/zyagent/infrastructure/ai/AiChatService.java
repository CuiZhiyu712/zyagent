package com.zyagent.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private final String apiKey;
    private final AiChatClient client;

    public AiChatService(
        @Value("${DEEPSEEK_API_KEY:}") String apiKey,
        AiChatClient client
    ) {
        this.apiKey = apiKey;
        this.client = client;
    }

    public String complete(String systemPrompt, String userPrompt) {
        if (!hasApiKey()) {
            return "DeepSeek API Key 尚未配置。当前返回本地占位回答：\n\n" + localAnswer(systemPrompt, userPrompt);
        }
        try {
            return client.complete(systemPrompt, userPrompt);
        } catch (Exception ex) {
            return "Spring AI DeepSeek 调用失败：" + ex.getMessage();
        }
    }

    public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
        if (!hasApiKey()) {
            for (String token : localAnswer(systemPrompt, userPrompt).split("(?<=\\G.{8})")) {
                if (!token.isBlank()) {
                    handler.onToken(token);
                }
            }
            return;
        }
        try {
            client.stream(systemPrompt, userPrompt, handler);
        } catch (Exception ex) {
            throw new IllegalStateException("Spring AI DeepSeek streaming failed: " + ex.getMessage(), ex);
        }
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String localAnswer(String systemPrompt, String userPrompt) {
        return "角色：" + systemPrompt
            + "\n任务：" + userPrompt
            + "\n建议：先上传简历、项目文档和目标 JD，再生成匹配报告、模拟面试与复盘计划。";
    }
}
