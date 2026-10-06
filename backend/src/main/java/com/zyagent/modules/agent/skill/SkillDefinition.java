package com.zyagent.modules.agent.skill;

import com.zyagent.modules.agent.AgentMode;

import java.util.List;

public record SkillDefinition(
    String id,
    String name,
    String description,
    AgentMode mode,
    List<String> toolNames,
    List<String> planSteps
) {
}
