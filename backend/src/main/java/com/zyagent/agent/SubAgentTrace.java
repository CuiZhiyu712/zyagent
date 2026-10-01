package com.zyagent.agent;

import java.util.List;

public record SubAgentTrace(
    SubAgentRole role,
    SubAgentStatus status,
    String summary,
    long durationMs,
    String errorMessage,
    List<String> artifactIds
) {
    public SubAgentTrace {
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }

    public static SubAgentTrace success(SubAgentRole role, String summary, long durationMs, List<AgentArtifact> artifacts) {
        return new SubAgentTrace(
            role,
            SubAgentStatus.SUCCESS,
            summary,
            Math.max(1L, durationMs),
            null,
            artifacts.stream().map(AgentArtifact::artifactId).toList()
        );
    }

    public static SubAgentTrace failure(SubAgentRole role, String errorMessage, long durationMs) {
        return new SubAgentTrace(role, SubAgentStatus.FAILED, "子 Agent 执行失败", Math.max(1L, durationMs), errorMessage, List.of());
    }
}
