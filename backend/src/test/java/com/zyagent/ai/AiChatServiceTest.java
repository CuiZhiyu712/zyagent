package com.zyagent.ai;

import com.zyagent.job.TestAssertions;

import java.util.ArrayList;
import java.util.List;

public class AiChatServiceTest {
    public static void run() {
        returnsFallbackWhenApiKeyIsBlank();
        delegatesCompletionToSpringAiClientWhenConfigured();
        delegatesStreamingTokensToHandler();
        returnsReadableErrorWhenSpringAiCallFails();
    }

    private static void returnsFallbackWhenApiKeyIsBlank() {
        AiChatService service = new AiChatService("", new AiChatClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("Spring AI client should not be called without an API key");
            }

            @Override
            public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
                throw new AssertionError("Spring AI client should not be called without an API key");
            }
        });

        String answer = service.complete("学习导师", "解释 Redis");

        TestAssertions.isTrue(answer.contains("DeepSeek API Key 尚未配置"), "fallback message");
    }

    private static void delegatesCompletionToSpringAiClientWhenConfigured() {
        CapturingAiChatClient client = new CapturingAiChatClient();
        AiChatService service = new AiChatService("test-key", client);

        String answer = service.complete("学习导师", "解释 Redis");

        TestAssertions.equals("真实回答", answer, "answer");
        TestAssertions.equals("学习导师", client.systemPrompt, "system prompt");
        TestAssertions.equals("解释 Redis", client.userPrompt, "user prompt");
    }

    private static void delegatesStreamingTokensToHandler() {
        CapturingAiChatClient client = new CapturingAiChatClient();
        AiChatService service = new AiChatService("test-key", client);
        List<String> tokens = new ArrayList<>();

        service.stream("学习导师", "解释 Redis", tokens::add);

        TestAssertions.equals(List.of("真实", "回答"), tokens, "streaming tokens");
        TestAssertions.equals("学习导师", client.systemPrompt, "stream system prompt");
        TestAssertions.equals("解释 Redis", client.userPrompt, "stream user prompt");
    }

    private static void returnsReadableErrorWhenSpringAiCallFails() {
        AiChatService service = new AiChatService("test-key", new AiChatClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("quota exceeded");
            }

            @Override
            public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
                throw new IllegalStateException("quota exceeded");
            }
        });

        String answer = service.complete("学习导师", "解释 Redis");

        TestAssertions.isTrue(answer.contains("Spring AI DeepSeek 调用失败：quota exceeded"), "readable Spring AI error");
    }

    private static class CapturingAiChatClient implements AiChatClient {
        private String systemPrompt;
        private String userPrompt;

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return "真实回答";
        }

        @Override
        public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            handler.onToken("真实");
            handler.onToken("回答");
        }
    }
}
