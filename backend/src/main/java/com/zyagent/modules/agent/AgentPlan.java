package com.zyagent.modules.agent;

import java.util.List;

public record AgentPlan(
    AgentMode mode,
    List<AgentPlanStep> steps
) {
    public static AgentPlan planned(AgentMode mode, List<String> stepTitles, List<String> toolNames) {
        return new AgentPlan(mode, java.util.stream.IntStream.range(0, stepTitles.size())
            .mapToObj(index -> AgentPlanStep.planned(
                stepTitles.get(index),
                index < toolNames.size() ? toolNames.get(index) : null
            ))
            .toList());
    }
}
