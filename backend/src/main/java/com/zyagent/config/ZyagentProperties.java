package com.zyagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zyagent")
public record ZyagentProperties(
    Milvus milvus,
    Embedding embedding,
    JobCollect jobCollect,
    BossCollect bossCollect,
    Upload upload,
    Chat chat,
    Agent agent,
    Mcp mcp,
    StructuredOutput structuredOutput
) {
    public record Milvus(String host, int port, String collection) {
    }

    public record Embedding(String provider, String apiKey, String baseUrl, String model, int dimension) {
    }

    public record JobCollect(boolean enabled, String cron, int timeoutSeconds, int maxPagesPerSource, String keywords) {
    }

    public record BossCollect(boolean enabled, String city, String keywords, int maxPages, int maxJobs, String profileDir, boolean headless) {
    }

    public record Upload(String directory) {
    }

    public record Chat(Memory memory) {
    }

    public record Memory(int maxMessages) {
    }

    public record Agent(int replanMaxAttempts) {
    }

    public record Mcp(boolean enabled) {
    }

    public record StructuredOutput(boolean enabled) {
    }
}
