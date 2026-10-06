package com.zyagent.modules.agent;

import com.zyagent.modules.agent.skill.SkillDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlannerAgent {
    public List<AgentArtifact> plan(AgentRouteDecision route, SkillDefinition skill, String message) {
        List<String> tasks = skill.planSteps().isEmpty()
            ? List.of("理解用户目标", "补充必要证据", "生成可执行回答")
            : skill.planSteps();
        String summary = "Planner 生成 " + tasks.size() + " 个任务，路由到 " + readableCategory(route.category()) + "。";
        return List.of(new AgentArtifact(
            id("plan"),
            SubAgentRole.PLANNER,
            "plan",
            summary,
            Map.of(
                "category", route.category().name(),
                "skillId", skill.id(),
                "tasks", tasks,
                "expectedTools", skill.toolNames(),
                "inputPreview", summarize(message)
            ),
            List.of(),
            0.82
        ));
    }

    private String readableCategory(AgentRouteCategory category) {
        return switch (category) {
            case RESUME -> "简历优化";
            case JOB -> "岗位分析";
            case INTERVIEW -> "面试训练";
            case STUDY_PLAN -> "学习计划";
            case KNOWLEDGE_QA -> "知识问答";
            case REVIEW -> "复盘改进";
            case CHAT -> "对话澄清";
            case CLARIFY -> "意图澄清";
            case GENERAL -> "通用问答";
        };
    }

    private String summarize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String id(String type) {
        return type + "-" + UUID.randomUUID();
    }
}
