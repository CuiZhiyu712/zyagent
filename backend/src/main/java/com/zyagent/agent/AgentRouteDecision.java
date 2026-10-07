package com.zyagent.agent;

import java.util.List;

public record AgentRouteDecision(
    AgentRouteCategory category,
    String skillId,
    String skillName,
    List<String> matchedKeywords,
    List<String> toolNames,
    String reason,
    RouteAction action,
    String previousSkillId
) {
    public AgentRouteDecision(AgentRouteCategory category, String skillId, String skillName,
                              List<String> matchedKeywords, List<String> toolNames, String reason) {
        this(category, skillId, skillName, matchedKeywords, toolNames, reason, RouteAction.SWITCH, null);
    }

    /** 是否在沿用上一轮的任务，而不是新开一项任务。 */
    public boolean continuesPreviousTask() {
        return action == RouteAction.CONTINUE;
    }

    public boolean needsClarification() {
        return action == RouteAction.CLARIFY;
    }

    /** 是否发生了跨 Skill 的切换（首次路由不算切换）。 */
    public boolean switchedSkill() {
        return previousSkillId != null && !previousSkillId.equals(skillId);
    }

    public AgentRouteDecision withAction(RouteAction nextAction, String previousSkill, String nextReason) {
        return new AgentRouteDecision(category, skillId, skillName, matchedKeywords, toolNames,
            nextReason, nextAction, previousSkill);
    }
}
