package com.zyagent.agent;

public record ChatMemorySnapshot(
    int recentMessageCount,
    String summary
) {
    public static ChatMemorySnapshot empty() {
        return new ChatMemorySnapshot(0, "");
    }
}
