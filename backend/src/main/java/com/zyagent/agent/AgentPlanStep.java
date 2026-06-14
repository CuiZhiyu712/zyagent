package com.zyagent.agent;

public record AgentPlanStep(
    String title,
    String toolName,
    AgentStepStatus status,
    String errorMessage,
    long durationMs
) {
    public static AgentPlanStep planned(String title, String toolName) {
        return new AgentPlanStep(title, toolName, AgentStepStatus.PLANNED, null, 0L);
    }

    public AgentPlanStep running() {
        return new AgentPlanStep(title, toolName, AgentStepStatus.RUNNING, null, durationMs);
    }

    public AgentPlanStep success(long durationMs) {
        return new AgentPlanStep(title, toolName, AgentStepStatus.SUCCESS, null, durationMs);
    }

    public AgentPlanStep failed(String errorMessage, long durationMs) {
        return new AgentPlanStep(title, toolName, AgentStepStatus.FAILED, errorMessage, durationMs);
    }

    public AgentPlanStep replanned(String reason) {
        return new AgentPlanStep(title, toolName, AgentStepStatus.REPLANNED, reason, durationMs);
    }
}
