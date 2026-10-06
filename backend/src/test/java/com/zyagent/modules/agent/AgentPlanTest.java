package com.zyagent.modules.agent;

import com.zyagent.modules.job.TestAssertions;

import java.util.List;

public class AgentPlanTest {
    public static void run() {
        planStepsExposeExecutionState();
    }

    private static void planStepsExposeExecutionState() {
        AgentPlanStep step = AgentPlanStep.planned("检索个人知识库", "search_personal_knowledge")
            .running()
            .success(12);
        AgentPlan plan = new AgentPlan(AgentMode.LEARNING_TUTOR, List.of(step));

        TestAssertions.equals(AgentStepStatus.SUCCESS, plan.steps().get(0).status(), "step status");
        TestAssertions.equals("search_personal_knowledge", plan.steps().get(0).toolName(), "tool name");
        TestAssertions.equals(12L, plan.steps().get(0).durationMs(), "duration");
    }
}
