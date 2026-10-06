package com.zyagent.infrastructure.config;

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
    Task task,
    Retrieval retrieval,
    Interview interview,
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

    /**
     * 可追踪 Agent task 的执行上限与持久化策略。
     *
     * <p>{@code ownerId} 是当前无认证单用户模式下的默认归属，认证接入后应改为从身份上下文读取。
     */
    public record Task(
        String ownerId,
        int maxPlanSteps,
        int maxToolCalls,
        long timeoutMs,
        long toolTimeoutMs,
        int maxRetries,
        boolean strictPersistence
    ) {
    }

    public record Mcp(boolean enabled) {
    }

    /**
     * Hybrid 检索参数：每路召回候选数、RRF 融合上限与常数、最终引用数量，以及可插拔 reranker 配置。
     */
    public record Retrieval(
        int vectorTopK,
        int keywordTopK,
        int fuseLimit,
        int rankConstant,
        int rerankLimit,
        Rerank rerank
    ) {
    }

    /** 外部 cross-encoder 重排配置；未启用或未配置 endpoint 时使用本地 RRF 兜底。 */
    public record Rerank(
        boolean enabled,
        String endpoint,
        long timeoutMs,
        int maxCandidates,
        int maxContentChars
    ) {
    }

    /** 模拟面试：每会话最大轮数、每题最大追问数与模型调用超时。 */
    public record Interview(int maxTurns, int maxFollowUps, long modelTimeoutMs) {
    }

    public record StructuredOutput(boolean enabled) {
    }
}
