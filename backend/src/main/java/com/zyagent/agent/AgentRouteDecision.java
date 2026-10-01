package com.zyagent.agent;

import java.util.List;

public record AgentRouteDecision(
    AgentRouteCategory category,
    String skillId,
    String skillName,
    List<String> matchedKeywords,
    List<String> toolNames,
    String reason
) {
}
