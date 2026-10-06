package com.zyagent.modules.agent;

import java.util.List;

public record CollaborationTrace(
    List<SubAgentTrace> agents,
    List<AgentArtifact> artifacts,
    String finalReview
) {
    public CollaborationTrace {
        agents = agents == null ? List.of() : List.copyOf(agents);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        finalReview = finalReview == null ? "" : finalReview;
    }

    public static CollaborationTrace empty() {
        return new CollaborationTrace(List.of(), List.of(), "");
    }
}
