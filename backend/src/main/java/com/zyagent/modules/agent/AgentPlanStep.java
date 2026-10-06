package com.zyagent.modules.agent;

public record AgentPlanStep(
    String stepId,
    String title,
    String toolName,
    AgentStepStatus status,
    String errorMessage,
    long durationMs,
    int attempt,
    String inputSummary,
    String outputSummary,
    java.util.List<String> evidenceRefs
) {
    public AgentPlanStep(String title, String toolName, AgentStepStatus status, String errorMessage, long durationMs) {
        this(null, title, toolName, status, errorMessage, durationMs, 1, null, null, java.util.List.of());
    }

    public static AgentPlanStep planned(String title, String toolName) {
        return new AgentPlanStep(java.util.UUID.randomUUID().toString(), title, toolName, AgentStepStatus.PLANNED, null, 0L, 1, null, null, java.util.List.of());
    }

    public AgentPlanStep running() {
        return copy(AgentStepStatus.RUNNING, null, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep success(long durationMs) {
        return copy(AgentStepStatus.SUCCESS, null, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep failed(String errorMessage, long durationMs) {
        return copy(AgentStepStatus.FAILED, errorMessage, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep retrying(String reason) {
        return copy(AgentStepStatus.RETRYING, reason, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep skipped(String reason) {
        return copy(AgentStepStatus.SKIPPED, reason, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep replanned(String reason) {
        return copy(AgentStepStatus.REPLANNED, reason, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep withAttempt(int attempt) {
        return copy(status, errorMessage, durationMs, Math.max(1, attempt), inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep withInputSummary(String inputSummary) {
        return copy(status, errorMessage, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep withOutputSummary(String outputSummary) {
        return copy(status, errorMessage, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }

    public AgentPlanStep withEvidenceRefs(java.util.List<String> evidenceRefs) {
        return copy(status, errorMessage, durationMs, attempt, inputSummary, outputSummary, evidenceRefs == null ? java.util.List.of() : evidenceRefs);
    }

    private AgentPlanStep copy(
        AgentStepStatus status,
        String errorMessage,
        long durationMs,
        int attempt,
        String inputSummary,
        String outputSummary,
        java.util.List<String> evidenceRefs
    ) {
        return new AgentPlanStep(stepId, title, toolName, status, errorMessage, durationMs, attempt, inputSummary, outputSummary, evidenceRefs);
    }
}
