package com.zyagent.agent;

import com.zyagent.tool.ToolResult;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.skill.SkillDefinition;

import java.util.List;

public record AgentResult(
    AgentMode mode,
    SkillDefinition skill,
    AgentPlan plan,
    List<ToolResult> toolResults,
    DocumentSearchResponse references,
    String answer
) {
}
