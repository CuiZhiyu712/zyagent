package com.zyagent.agent.task;

import com.zyagent.agent.AgentMode;

import java.time.LocalDateTime;

public record AgentTask(
    String id,
    String ownerId,
    String sessionId,
    AgentMode mode,
    AgentTaskState state,
    String requestText,
    String resultText,
    String errorCode,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime updatedAt,
    String idempotencyKey
) {
    public AgentTask start() {
        return new AgentTask(id, ownerId, sessionId, mode, state.transitionTo(AgentTaskState.RUNNING), requestText,
            resultText, errorCode, errorMessage, createdAt, startedAt == null ? LocalDateTime.now() : startedAt,
            finishedAt, LocalDateTime.now(), idempotencyKey);
    }

    public AgentTask succeed(String result) {
        return finish(AgentTaskState.SUCCEEDED, result, null, null);
    }

    public AgentTask fail(String code, String message) {
        return finish(AgentTaskState.FAILED, null, code, message);
    }

    public AgentTask timeout(String message) {
        return finish(AgentTaskState.TIMED_OUT, null, "TIMEOUT", message);
    }

    private AgentTask finish(AgentTaskState next, String result, String code, String message) {
        return new AgentTask(id, ownerId, sessionId, mode, state.transitionTo(next), requestText, result, code, message,
            createdAt, startedAt, LocalDateTime.now(), LocalDateTime.now(), idempotencyKey);
    }
}
