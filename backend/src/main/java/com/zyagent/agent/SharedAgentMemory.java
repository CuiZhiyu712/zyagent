package com.zyagent.agent;

import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

public class SharedAgentMemory {
    private final AgentRouteDecision routeDecision;
    private final String userMessage;
    private final ChatMemorySnapshot memorySnapshot;
    private final List<ToolResult> toolResults;
    private final DocumentSearchResponse references;
    private final List<AgentArtifact> artifacts = new ArrayList<>();

    public SharedAgentMemory(
        AgentRouteDecision routeDecision,
        String userMessage,
        ChatMemorySnapshot memorySnapshot,
        List<ToolResult> toolResults,
        DocumentSearchResponse references
    ) {
        this.routeDecision = routeDecision;
        this.userMessage = userMessage;
        this.memorySnapshot = memorySnapshot == null ? ChatMemorySnapshot.empty() : memorySnapshot;
        this.toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        this.references = references;
    }

    public AgentRouteDecision routeDecision() {
        return routeDecision;
    }

    public String userMessage() {
        return userMessage;
    }

    public ChatMemorySnapshot memorySnapshot() {
        return memorySnapshot;
    }

    public List<ToolResult> toolResults() {
        return toolResults;
    }

    public DocumentSearchResponse references() {
        return references;
    }

    public List<AgentArtifact> artifacts() {
        return List.copyOf(artifacts);
    }

    public void addArtifacts(List<AgentArtifact> newArtifacts) {
        if (newArtifacts != null) {
            artifacts.addAll(newArtifacts);
        }
    }
}
