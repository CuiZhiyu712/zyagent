package com.zyagent.agent;

import java.util.List;
import java.util.Map;

public record AgentArtifact(
    String artifactId,
    SubAgentRole producer,
    String type,
    String summary,
    Map<String, Object> payload,
    List<String> evidenceRefs,
    double confidence
) {
    public AgentArtifact {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        confidence = Math.max(0D, Math.min(1D, confidence));
    }
}
