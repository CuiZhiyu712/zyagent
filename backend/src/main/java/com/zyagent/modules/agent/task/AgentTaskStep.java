package com.zyagent.modules.agent.task;

import com.zyagent.modules.agent.AgentPlanStep;

import java.time.LocalDateTime;

public record AgentTaskStep(
    String id,
    String taskId,
    int stepNo,
    String title,
    String toolName,
    String status,
    String inputSummary,
    String outputSummary,
    String errorMessage,
    int attempt,
    long durationMs,
    LocalDateTime createdAt
) {
    public static AgentTaskStep from(String taskId, int stepNo, AgentPlanStep step) {
        return new AgentTaskStep(step.stepId(), taskId, stepNo, step.title(), step.toolName(), step.status().name(),
            step.inputSummary(), step.outputSummary(), step.errorMessage(), step.attempt(), step.durationMs(), LocalDateTime.now());
    }
}
