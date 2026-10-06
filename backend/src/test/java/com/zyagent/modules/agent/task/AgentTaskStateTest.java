package com.zyagent.modules.agent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskStateTest {
    @Test
    void allowsNormalTaskLifecycle() {
        AgentTaskState state = AgentTaskState.PENDING;

        state = state.transitionTo(AgentTaskState.RUNNING);
        state = state.transitionTo(AgentTaskState.SUCCEEDED);

        org.junit.jupiter.api.Assertions.assertEquals(AgentTaskState.SUCCEEDED, state);
    }

    @Test
    void rejectsTerminalStateRollback() {
        assertThrows(IllegalStateException.class,
            () -> AgentTaskState.SUCCEEDED.transitionTo(AgentTaskState.RUNNING));
    }

    @Test
    void allowsInterruptedRecoveryAsExplicitRetry() {
        assertDoesNotThrow(() -> AgentTaskState.INTERRUPTED.transitionTo(AgentTaskState.PENDING));
    }
}
