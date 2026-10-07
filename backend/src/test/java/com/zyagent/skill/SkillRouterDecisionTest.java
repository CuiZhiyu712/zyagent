package com.zyagent.skill;

import com.zyagent.agent.AgentRouteCategory;
import com.zyagent.agent.AgentRouteDecision;
import com.zyagent.agent.ChatMemorySnapshot;
import com.zyagent.job.JobDescriptionParser;
import com.zyagent.job.TestAssertions;

public class SkillRouterDecisionTest {
    public static void run() {
        routesJobMessagesWithExplainableDecision();
        explicitModeStillProducesRouteDecision();
        routesInterviewWrapUpFromConversationContext();
        routesDayStudyFromExistingPlanContext();
        routesInitialStudyPlanBeforeInterviewKeyword();
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

    private static void routesInterviewWrapUpFromConversationContext() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));
        ChatMemorySnapshot memory = new ChatMemorySnapshot(6,
            "用户：继续模拟面试\n助手：请介绍你的后端项目\n用户：结束面试模拟，给我 Day1 的问题和答案以及学习思路");

        AgentRouteDecision decision = router.routeDecision(null,
            "结束面试模拟，帮我给出day1的问题和答案，以及学习思路", memory);

        TestAssertions.equals(AgentRouteCategory.REVIEW, decision.category(), "contextual wrap-up category");
        TestAssertions.equals("review_skill", decision.skillId(), "contextual wrap-up skill");
        TestAssertions.isTrue(decision.toolNames().contains("generate_study_plan"), "review study plan tool");
        TestAssertions.isTrue(!decision.toolNames().contains("generate_interview_questions"), "wrap-up must not generate another interview");
        TestAssertions.isTrue(decision.reason().contains("上下文"), "contextual route reason");

        AgentRouteDecision answerDecision = router.routeDecision(null,
            "我的项目也涉及岗位匹配，但请继续评价刚才的回答", memory);
        TestAssertions.equals(AgentRouteCategory.INTERVIEW, answerDecision.category(), "interview answer keeps interview route");

        AgentRouteDecision switchDecision = router.routeDecision(null,
            "切换到岗位分析，帮我匹配岗位", memory);
        TestAssertions.equals(AgentRouteCategory.JOB, switchDecision.category(), "explicit job switch route");
    }

    private static void routesDayStudyFromExistingPlanContext() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));
        ChatMemorySnapshot memory = new ChatMemorySnapshot(6,
            "助手：全覆盖学习计划清单，共约365题，按14天计划组织。\n"
                + "Day1｜模块一：自我介绍与项目总览（Q1–10）\n"
                + "Day2｜模块二：求职Agent平台架构与Agent基础（Q11–40）");

        AgentRouteDecision decision = router.routeDecision(null, "现在进行Day1的学习", memory);

        // DayN 是学习计划任务的进度（currentDay），不是"复盘"，因此仍归学习导师。
        TestAssertions.equals(AgentRouteCategory.STUDY_PLAN, decision.category(), "plan day route category");
        TestAssertions.equals("learning_tutor_skill", decision.skillId(), "plan day skill id");
        TestAssertions.isTrue(decision.reason().contains("学习计划上下文"), "plan context route reason");
    }

    private static void routesInitialStudyPlanBeforeInterviewKeyword() {
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));
        AgentRouteDecision decision = router.routeDecision(null,
            "基于面试问题清单，帮我制定学习计划并覆盖所有问题");

        TestAssertions.equals(AgentRouteCategory.STUDY_PLAN, decision.category(), "initial study plan category");
        TestAssertions.equals("learning_tutor_skill", decision.skillId(), "initial study plan skill");
    }
}
