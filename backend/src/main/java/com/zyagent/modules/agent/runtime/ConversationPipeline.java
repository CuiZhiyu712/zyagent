package com.zyagent.modules.agent.runtime;

import com.zyagent.modules.agent.AgentMode;
import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.AgentPlan;
import com.zyagent.modules.agent.AgentRunMetrics;
import com.zyagent.modules.agent.AgentRouteDecision;
import com.zyagent.modules.agent.ChatMemoryService;
import com.zyagent.modules.agent.ChatMemorySnapshot;
import com.zyagent.modules.agent.CollaborationTrace;
import com.zyagent.modules.agent.MultiAgentCoordinator;
import com.zyagent.modules.agent.skill.IntentSignals;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.skill.SkillRouter;
import com.zyagent.modules.agent.task.AgentExecutionListener;
import com.zyagent.modules.agent.task.AgentTask;
import com.zyagent.modules.agent.task.AgentTaskService;
import com.zyagent.modules.agent.task.AgentTaskState;
import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationPipeline {
    private final SkillRouter router;
    private final ChatMemoryService memory;
    private final SkillToolExecutor tools;
    private final AgentTaskService tasks;
    private final MultiAgentCoordinator collaboration = new MultiAgentCoordinator();

    public ConversationPipeline(SkillRouter router, ChatMemoryService memory, SkillToolExecutor tools, AgentTaskService tasks) {
        this.router = router;
        this.memory = memory;
        this.tools = tools;
        this.tasks = tasks;
    }

    public AgentOrchestrator.AgentRun prepare(AgentTask existingTask, AgentMode mode, String message, String sessionId,
                                               String idempotencyKey, AgentExecutionListener listener) {
        ChatMemorySnapshot snapshot = memory.snapshot(sessionId);
        AgentRouteDecision decision = router.routeDecision(mode, message, snapshot);
        SkillDefinition skill = router.route(decision);
        memory.saveTaskState(sessionId, decision.category() == null ? null : decision.category().name(),
            skill.id(), IntentSignals.studyDayNumber(message));
        AgentTask task = existingTask == null ? tasks.create(sessionId, skill.mode(), message, idempotencyKey) : existingTask;
        if (task.state() != AgentTaskState.PENDING) {
            return new AgentOrchestrator.AgentRun(task, skill, decision, new AgentPlan(skill.mode(), List.of()), List.of(), null,
                AgentRunMetrics.from(List.of(), null, 0), snapshot, CollaborationTrace.empty(), true);
        }
        AgentExecutionListener stepListener = listener == null ? persistingListener() : listener;
        SkillToolExecutor.Execution execution = tools.execute(skill, message, task.id(), stepListener);
        DocumentSearchResponse references = execution.toolResults().stream()
            .filter(com.zyagent.modules.agent.tool.ToolResult::success)
            .map(com.zyagent.modules.agent.tool.ToolResult::output)
            .filter(DocumentSearchResponse.class::isInstance)
            .map(DocumentSearchResponse.class::cast)
            .findFirst().orElse(null);
        AgentRunMetrics metrics = AgentRunMetrics.from(execution.toolResults(), references, execution.retryCount());
        CollaborationTrace trace = collaboration.coordinate(decision, skill, message, snapshot, execution.toolResults(), references);
        AgentTask started = tasks.start(task);
        return new AgentOrchestrator.AgentRun(started, skill, decision, new AgentPlan(skill.mode(), execution.steps()),
            execution.toolResults(), references, metrics, snapshot, trace, false);
    }

    private AgentExecutionListener persistingListener() {
        return new AgentExecutionListener() {
            @Override public void onStepStarted(String taskId, int stepNo, com.zyagent.modules.agent.AgentPlanStep step) {
                tasks.saveStep(taskId, stepNo, step);
            }
            @Override public void onStepFinished(String taskId, int stepNo, com.zyagent.modules.agent.AgentPlanStep step) {
                tasks.saveStep(taskId, stepNo, step);
            }
        };
    }
}
