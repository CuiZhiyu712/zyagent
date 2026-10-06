package com.zyagent.modules.agent;

public record TokenUsage(
    int promptTokens,
    int completionTokens,
    int totalTokens,
    boolean estimated
) {
    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0, true);
    }
}
