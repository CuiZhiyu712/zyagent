package com.zyagent.skill;

import com.zyagent.agent.AgentMode;
import com.zyagent.job.JobDescriptionParser;

public class SkillRouterTest {
    public static void run() {
        routesJobMessagesToJobAnalysisSkill();
        explicitAgentModeOverridesKeywordRouting();
        routesInterviewMessagesToInterviewSkill();
    }

    private static void routesJobMessagesToJobAnalysisSkill() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

        SkillDefinition skill = router.route(null, "请分析这个 Java 后端 JD，并提取招聘关键词");

        if (!"job_analysis_skill".equals(skill.id())) {
            throw new AssertionError("Expected job_analysis_skill, got " + skill.id());
        }
    }

    private static void explicitAgentModeOverridesKeywordRouting() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

        SkillDefinition skill = router.route(AgentMode.RESUME_COACH, "这是一段岗位 JD，请帮我优化简历");

        if (!"resume_coach_skill".equals(skill.id())) {
            throw new AssertionError("Expected resume_coach_skill, got " + skill.id());
        }
    }

    private static void routesInterviewMessagesToInterviewSkill() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

        SkillDefinition skill = router.route(null, "请作为面试官连续追问我的项目");

        if (!"interview_skill".equals(skill.id())) {
            throw new AssertionError("Expected interview_skill, got " + skill.id());
        }
    }
}
