package com.zyagent.agent;

import com.zyagent.job.JobDescriptionParser;
import com.zyagent.skill.IntentSignals;
import com.zyagent.skill.SkillCatalog;
import com.zyagent.skill.SkillRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文一致性回归测试。
 *
 * <p>覆盖真实缺陷：用户的一句元反馈（"上下文不一致啊"）被当作学习计划的"薄弱点"参数，
 * 触发了硬编码工具输出 "第 1 天：梳理基础概念 - 上下文不一致啊" 并污染了回答。
 */
class ContextConsistencyTest {
    private static final SkillRouter ROUTER = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

    @Test
    void metaFeedbackRoutesToToolFreeChatSkill() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "上下文不一致啊");

        assertEquals(AgentRouteCategory.CHAT, decision.category(), "meta feedback is a chat turn");
        assertEquals("chat_skill", decision.skillId());
        assertTrue(decision.toolNames().isEmpty(), "chat skill must not expose business tools");
    }

    @Test
    void metaFeedbackIsRejectedAsToolContent() {
        assertFalse(IntentSignals.hasContentForTool("generate_study_plan", "上下文不一致啊"),
            "meta feedback must not become a weakness input");
        assertFalse(IntentSignals.hasContentForTool("generate_study_plan", "你搞错了，答非所问"),
            "correction must not become a weakness input");
    }

    @Test
    void executingAnExistingPlanDayDoesNotRegenerateThePlan() {
        assertFalse(IntentSignals.hasContentForTool("generate_study_plan", "先开始第一天的计划学习"),
            "running day 1 of an existing plan must not regenerate a template plan");
        assertTrue(IntentSignals.hasContentForTool("generate_study_plan", "我的薄弱点是 MySQL 索引和事务"),
            "a real weakness is still a valid input");
    }

    @Test
    void dayMentionIsCaseInsensitive() {
        assertTrue(IntentSignals.mentionsStudyDay("现在进行Day1的学习"));
        assertTrue(IntentSignals.mentionsStudyDay("先开始第一天的计划学习"));
        assertFalse(IntentSignals.mentionsStudyDay("帮我制定一份学习计划"));
    }

    @Test
    void selfContainedNewQuestionDoesNotStayStuckInPreviousMode() {
        ChatMemorySnapshot afterPlan = new ChatMemorySnapshot(6,
            "助手：第 1 天：梳理基础概念，先讲 JVM 内存结构。");

        AgentRouteDecision decision = ROUTER.routeDecision(null, "帮我分析这个 JD 岗位要求", afterPlan);

        assertEquals("job_analysis_skill", decision.skillId(),
            "a new self-contained question must re-route instead of staying in the previous skill");
    }

    @Test
    void interviewContextOnlySticksForContinuationRequests() {
        ChatMemorySnapshot afterInterview = new ChatMemorySnapshot(6,
            "助手：面试官：请介绍你的后端项目。\n用户：我做过缓存优化。");

        AgentRouteDecision fresh = ROUTER.routeDecision(null, "什么是 JVM 垃圾回收", afterInterview);
        assertFalse(AgentRouteCategory.INTERVIEW == fresh.category(),
            "a self-contained new question must not inherit the interview flow");

        AgentRouteDecision continuation = ROUTER.routeDecision(null, "继续评价刚才的回答", afterInterview);
        assertEquals(AgentRouteCategory.INTERVIEW, continuation.category(),
            "an explicit continuation still keeps the interview flow");

        AgentRouteDecision switched = ROUTER.routeDecision(null, "切换到岗位分析，帮我匹配岗位", afterInterview);
        assertEquals(AgentRouteCategory.JOB, switched.category(), "explicit switch still wins");
    }

    @Test
    void continuationSignalRequiresExplicitWording() {
        assertTrue(IntentSignals.inheritsPreviousTask("继续"));
        assertTrue(IntentSignals.inheritsPreviousTask("接着刚才的说"));
        assertTrue(IntentSignals.inheritsPreviousTask("现在进行Day1的学习"));
        assertFalse(IntentSignals.inheritsPreviousTask("什么是 JVM 垃圾回收"));
        assertFalse(IntentSignals.inheritsPreviousTask("帮我分析这个 JD 岗位要求"));
    }

    @Test
    void planDayRequestStaysInThePlanOwningSkill() {
        ChatMemorySnapshot memory = new ChatMemorySnapshot(6,
            "助手：全覆盖学习计划清单，共约365题，按14天计划组织。\n"
                + "Day1｜模块一：自我介绍与项目总览（Q1–10）\n"
                + "Day2｜模块二：求职Agent平台架构与Agent基础（Q11–40）");

        AgentRouteDecision decision = ROUTER.routeDecision(null, "先开始第一天的计划学习", memory);

        // DayN 是学习计划任务的进度，不是"复盘"；计划归学习导师所有。
        assertEquals("learning_tutor_skill", decision.skillId(), "day execution continues the plan-owning skill");
        assertEquals(AgentRouteCategory.STUDY_PLAN, decision.category());
    }

    @Test
    void extractsStudyDayNumberForTaskProgress() {
        assertEquals(1, IntentSignals.studyDayNumber("现在进行Day1的学习"));
        assertEquals(2, IntentSignals.studyDayNumber("开始第二天"));
        assertEquals(3, IntentSignals.studyDayNumber("第3天继续"));
        assertEquals(0, IntentSignals.studyDayNumber("帮我制定一份学习计划"));
    }

    @Test
    void domainRequestsStillRouteToTheirSkills() {
        assertEquals("job_analysis_skill", ROUTER.routeDecision(null, "帮我分析这个 JD 岗位要求").skillId());
        assertEquals("learning_tutor_skill",
            ROUTER.routeDecision(null, "基于面试问题清单，帮我制定学习计划并覆盖所有问题").skillId(),
            "initial plan request must not be swallowed by chat");
    }

    @Test
    void studyPlanCreationWinsOverOtherDomainKeywords() {
        // "面试" 同时是面试 Skill 的触发词；制定学习计划的请求必须规则优先进入学习导师，
        // 否则一旦被判成复盘/面试，最近的助手 Skill 会把后续多轮都带偏。
        assertEquals("learning_tutor_skill",
            ROUTER.routeDecision(null, "帮我制定7天面试学习计划", ChatMemorySnapshot.empty()).skillId());
        assertEquals("learning_tutor_skill",
            ROUTER.routeDecision(null, "基于面试问题清单制定学习计划并覆盖所有问题", ChatMemorySnapshot.empty()).skillId());
    }

    @Test
    void promptCarriesGroundingAndContinuityConstraints() {
        PromptAdvisorChain advisor = new PromptAdvisorChain();

        String prompt = advisor.render("上下文不一致啊", "用户：先开始第一天的计划学习", List.of());

        assertTrue(prompt.contains("不得编造"), "prompt forbids fabricating numbers or plans");
        assertTrue(prompt.contains("未检索到相关证据"), "prompt requires admitting missing evidence");
        assertTrue(prompt.contains("不要开启新的业务产出"), "prompt keeps meta feedback from opening new output");
    }
}
