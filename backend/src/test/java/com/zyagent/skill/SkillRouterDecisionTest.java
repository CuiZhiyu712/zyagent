package com.zyagent.skill;

import com.zyagent.agent.AgentRouteCategory;
import com.zyagent.agent.AgentRouteDecision;
import com.zyagent.job.JobDescriptionParser;
import com.zyagent.job.TestAssertions;

public class SkillRouterDecisionTest {
    public static void run() {
        routesJobMessagesWithExplainableDecision();
        explicitModeStillProducesRouteDecision();
    }

    private static void routesJobMessagesWithExplainableDecision() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

        AgentRouteDecision decision = router.routeDecision(null, "帮我分析这个 JD 岗位要求，看看简历怎么匹配");

        TestAssertions.equals(AgentRouteCategory.JOB, decision.category(), "route category");
        TestAssertions.equals("job_analysis_skill", decision.skillId(), "skill id");
        TestAssertions.isTrue(decision.reason().contains("JD") || decision.reason().contains("岗位"), "route reason");
        TestAssertions.isTrue(decision.toolNames().contains("parse_job_description"), "candidate tool");
    }

    private static void explicitModeStillProducesRouteDecision() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

        AgentRouteDecision decision = router.routeDecision(com.zyagent.agent.AgentMode.INTERVIEWER, "随便问一个问题");

        TestAssertions.equals(AgentRouteCategory.INTERVIEW, decision.category(), "explicit route category");
        TestAssertions.equals("interview_skill", decision.skillId(), "explicit skill id");
    }
}
