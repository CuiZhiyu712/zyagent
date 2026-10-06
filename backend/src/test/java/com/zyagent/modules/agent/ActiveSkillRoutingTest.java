package com.zyagent.modules.agent;

import com.zyagent.modules.job.JobDescriptionParser;
import com.zyagent.modules.agent.skill.SkillCatalog;
import com.zyagent.modules.agent.skill.SkillRouter;
import com.zyagent.infrastructure.storage.ChatMessageView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Active Skill + 三态决策（CONTINUE / SWITCH / CLARIFY）+ 上下文隔离。
 *
 * <p>目的是消除多轮对话中 Skill 每轮重选造成的漂移与串线。
 */
class ActiveSkillRoutingTest {
    private static final SkillRouter ROUTER = new SkillRouter(new SkillCatalog(new JobDescriptionParser()));

    @Test
    void keepsActiveSkillWhenRequestBelongsToTheSameTask() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "我的项目经历是不是写得太简单了", after("resume_coach_skill"));

        assertEquals("resume_coach_skill", decision.skillId());
        assertTrue(decision.continuesPreviousTask(), "same-task request continues the active skill");
        assertEquals(RouteAction.CONTINUE, decision.action());
    }

    @Test
    void switchesWhenAnotherSkillClearlyTriggers() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "帮我分析这个 JD 岗位要求", after("resume_coach_skill"));

        assertEquals("job_analysis_skill", decision.skillId());
        assertEquals(RouteAction.SWITCH, decision.action());
        assertEquals("resume_coach_skill", decision.previousSkillId());
        assertTrue(decision.switchedSkill());
    }

    @Test
    void definitionalQuestionSwitchesAwayFromActiveSkill() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "什么是 JVM 垃圾回收", after("resume_coach_skill"));

        assertNotEquals("resume_coach_skill", decision.skillId(), "a new definitional question must re-route");
        assertFalse(decision.continuesPreviousTask());
    }

    @Test
    void explicitContinuationKeepsInterviewFlow() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "继续评价刚才的回答", after("interview_skill"));

        assertEquals("interview_skill", decision.skillId());
        assertTrue(decision.continuesPreviousTask());
    }

    @Test
    void referentialRequestWithoutActiveSkillAsksForClarification() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "帮我看看这个怎么样", ChatMemorySnapshot.empty());

        assertEquals("clarify_skill", decision.skillId());
        assertEquals(RouteAction.CLARIFY, decision.action());
        assertTrue(decision.needsClarification());
        assertTrue(decision.toolNames().isEmpty(), "clarify skill must not call tools");
    }

    @Test
    void firstTurnRoutesByKeywords() {
        AgentRouteDecision decision = ROUTER.routeDecision(null, "帮我分析这个 JD 岗位要求", ChatMemorySnapshot.empty());

        assertEquals("job_analysis_skill", decision.skillId());
        assertEquals(RouteAction.SWITCH, decision.action());
        assertEquals(null, decision.previousSkillId(), "first turn has no previous skill");
    }

    @Test
    void rendersOnlyTheCurrentSkillRun() {
        List<ChatMessageView> messages = List.of(
            message("u1", "user", "帮我优化简历", null),
            message("a1", "assistant", "简历建议A", "{\"skill\":\"resume_coach_skill\"}"),
            message("u2", "user", "帮我准备面试", null),
            message("a2", "assistant", "面试问题B", "{\"skill\":\"interview_skill\"}"));

        ChatMemoryContext context = new ChatMemoryContext(10);

        assertEquals("interview_skill", context.snapshot(messages).activeSkillId(), "active skill comes from the last assistant turn");

        String interviewScoped = context.renderForSkill(messages, "interview_skill");
        assertTrue(interviewScoped.contains("面试问题B"), "current task history kept");
        assertFalse(interviewScoped.contains("简历建议A"), "previous skill output must not leak into the new task");

        String resumeScoped = context.renderForSkill(messages, "resume_coach_skill");
        assertFalse(resumeScoped.contains("面试问题B"), "older skill must not see the newer task either");
    }

    private static ChatMemorySnapshot after(String activeSkillId) {
        return new ChatMemorySnapshot(4, "最近对话上下文：\n用户：上一轮请求", activeSkillId);
    }

    private static ChatMessageView message(String id, String role, String content, String referencesJson) {
        return new ChatMessageView(id, "session-1", role, content, referencesJson, LocalDateTime.now());
    }
}
