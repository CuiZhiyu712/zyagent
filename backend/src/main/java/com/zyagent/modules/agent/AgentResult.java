package com.zyagent.modules.agent;

import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.tool.ToolResult;

import java.util.List;

public record AgentResult(
    AgentMode mode,
    SkillDefinition skill,
    AgentPlan plan,
    List<ToolResult> toolResults,
    DocumentSearchResponse references,
    String answer,
    AgentRouteDecision routeDecision,
    TokenUsage tokenUsage,
    AgentRunMetrics runMetrics,
    ChatMemorySnapshot memorySnapshot,
    CollaborationTrace collaborationTrace
) {
    public AgentResult(
        AgentMode mode,
        SkillDefinition skill,
        AgentPlan plan,
        List<ToolResult> toolResults,
        DocumentSearchResponse references,
        String answer,
        AgentRouteDecision routeDecision,
        TokenUsage tokenUsage,
        AgentRunMetrics runMetrics,
        ChatMemorySnapshot memorySnapshot
    ) {
        this(
            mode,
            skill,
            plan,
            toolResults,
            references,
            answer,
            routeDecision,
            tokenUsage,
            runMetrics,
            memorySnapshot,
            CollaborationTrace.empty()
        );
    }

    public AgentResult(
        AgentMode mode,
        SkillDefinition skill,
        AgentPlan plan,
        List<ToolResult> toolResults,
        DocumentSearchResponse references,
        String answer
    ) {
        this(
            mode,
            skill,
            plan,
            toolResults,
            references,
            answer,
            null,
            TokenUsage.empty(),
            AgentRunMetrics.from(toolResults, references, 0),
            ChatMemorySnapshot.empty(),
            CollaborationTrace.empty()
        );
    }
}
