package com.zyagent.modules.agent;

import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

public class MultiAgentCoordinator {
    private final PlannerAgent plannerAgent;
    private final RetrieverAgent retrieverAgent;
    private final EvaluatorAgent evaluatorAgent;
    private final ReviewerAgent reviewerAgent;

    public MultiAgentCoordinator() {
        this(new PlannerAgent(), new RetrieverAgent(), new EvaluatorAgent(), new ReviewerAgent());
    }

    public MultiAgentCoordinator(
        PlannerAgent plannerAgent,
        RetrieverAgent retrieverAgent,
        EvaluatorAgent evaluatorAgent,
        ReviewerAgent reviewerAgent
    ) {
        this.plannerAgent = plannerAgent;
        this.retrieverAgent = retrieverAgent;
        this.evaluatorAgent = evaluatorAgent;
        this.reviewerAgent = reviewerAgent;
    }

    public CollaborationTrace coordinate(
        AgentRouteDecision route,
        SkillDefinition skill,
        String message,
        ChatMemorySnapshot memorySnapshot,
        List<ToolResult> toolResults,
        DocumentSearchResponse references
    ) {
        SharedAgentMemory memory = new SharedAgentMemory(route, message, memorySnapshot, toolResults, references);
        List<SubAgentTrace> agents = new ArrayList<>();
        run(SubAgentRole.PLANNER, agents, memory, () -> plannerAgent.plan(route, skill, message));
        run(SubAgentRole.RETRIEVER, agents, memory, () -> retrieverAgent.retrieve(memory));
        run(SubAgentRole.EVALUATOR, agents, memory, () -> evaluatorAgent.evaluate(memory));
        run(SubAgentRole.REVIEWER, agents, memory, () -> reviewerAgent.review(memory));
        String finalReview = memory.artifacts().stream()
            .filter(artifact -> artifact.producer() == SubAgentRole.REVIEWER)
            .reduce((first, second) -> second)
            .map(AgentArtifact::summary)
            .orElse("");
        return new CollaborationTrace(agents, memory.artifacts(), finalReview);
    }

    private void run(
        SubAgentRole role,
        List<SubAgentTrace> agents,
        SharedAgentMemory memory,
        ArtifactSupplier supplier
    ) {
        long started = System.nanoTime();
        try {
            List<AgentArtifact> artifacts = supplier.get();
            memory.addArtifacts(artifacts);
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            String summary = artifacts.isEmpty() ? role.name() + " 未产出 artifact" : artifacts.get(0).summary();
            agents.add(SubAgentTrace.success(role, summary, durationMs, artifacts));
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            agents.add(SubAgentTrace.failure(role, ex.getMessage(), durationMs));
        }
    }

    @FunctionalInterface
    private interface ArtifactSupplier {
        List<AgentArtifact> get();
    }
}
