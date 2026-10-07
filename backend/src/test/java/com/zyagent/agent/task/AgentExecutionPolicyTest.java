package com.zyagent.agent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionPolicyTest {
    @Test
    void enforcesPlanStepAndToolCallLimits() {
        AgentExecutionPolicy policy = new AgentExecutionPolicy(new AgentTaskLimits(2, 1, 1_000, 100, 0));

        assertTrue(policy.withinPlanSteps(1), "step below limit");
        assertFalse(policy.withinPlanSteps(2), "step at limit");
        assertTrue(policy.withinToolCalls(0), "no tool calls yet");
        assertFalse(policy.withinToolCalls(1), "tool call budget exhausted");
    }

    @Test
    void retriesOnlyIdempotentReadOnlyToolsWithinBudget() {
        AgentExecutionPolicy policy = new AgentExecutionPolicy(new AgentTaskLimits(5, 5, 1_000, 100, 1));

        assertTrue(policy.isIdempotentReadOnly("search_personal_knowledge"), "known read-only tool");
        assertFalse(policy.isIdempotentReadOnly("delete_documents"), "unknown tool is not retried");
        assertTrue(policy.canRetry("search_personal_knowledge", 1), "one retry allowed");
        assertFalse(policy.canRetry("search_personal_knowledge", 2), "retry budget exhausted");
        assertFalse(policy.canRetry("delete_documents", 1), "non read-only tool never retried");
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new AgentTaskLimits(0, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new AgentTaskLimits(1, 1, 1, 1, -1));
    }
}
