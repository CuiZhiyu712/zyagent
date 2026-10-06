package com.zyagent.modules.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAgentServiceTest {
    @Test
    void parsesValidEvaluation() {
        InterviewAgentService service = serviceReturning(
            "{\"technicalCorrectness\":4,\"completeness\":3,\"projectEvidence\":2,\"expressionStructure\":5,"
                + "\"explanations\":[\"ok\"],\"evidence\":[\"proj-1\"],\"followUp\":false,\"followUpQuestion\":null}");

        AnswerAssessment assessment = service.assess(session(), "q", "a", true);

        assertTrue(assessment.usable());
        assertEquals(4, assessment.evaluation().technicalCorrectness());
        assertEquals(2, assessment.evaluation().projectEvidence());
        assertEquals(List.of("proj-1"), assessment.evaluation().evidenceRefs());
    }

    @Test
    void marksFallbackWhenEvaluationJsonIsInvalid() {
        InterviewAgentService service = serviceReturning("这不是 JSON");

        AnswerAssessment assessment = service.assess(session(), "q", "a", true);

        assertFalse(assessment.usable(), "invalid output must not be presented as usable");
        assertFalse(assessment.evaluation().usable(), "evaluation flagged unusable");
        assertEquals(0.0, assessment.evaluation().average(), "no fabricated score");
        assertTrue(assessment.note().contains("无法解析"), "fallback reason recorded");
    }

    @Test
    void marksFallbackWhenScoresMissing() {
        InterviewAgentService service = serviceReturning("{\"explanations\":[]}");

        AnswerAssessment assessment = service.assess(session(), "q", "a", true);

        assertFalse(assessment.usable());
        assertTrue(assessment.note().contains("无法解析"));
    }

    @Test
    void followUpOnlyWhenAllowedAndProvided() {
        String withFollowUp = "{\"technicalCorrectness\":2,\"completeness\":2,\"projectEvidence\":1,"
            + "\"expressionStructure\":2,\"explanations\":[],\"evidence\":[],\"followUp\":true,"
            + "\"followUpQuestion\":\"补充项目证据\"}";

        AnswerAssessment blocked = serviceReturning(withFollowUp).assess(session(), "q", "a", false);
        assertFalse(blocked.followUp(), "follow-up suppressed when not allowed");

        AnswerAssessment allowed = serviceReturning(withFollowUp).assess(session(), "q", "a", true);
        assertTrue(allowed.followUp());
        assertEquals("补充项目证据", allowed.followUpQuestion());
    }

    @Test
    void fallsBackToLocalQuestionWhenQuestionOutputInvalid() {
        InterviewAgentService service = serviceReturning("oops");

        QuestionPlan plan = service.nextQuestion(session(), List.of());

        assertFalse(plan.usable());
        assertFalse(plan.question().isBlank(), "a local fallback question is still provided");
        assertTrue(plan.note().contains("兜底"));
    }

    private static InterviewSession session() {
        return InterviewSession.create("owner-1", "job-1", null, "项目深挖", "中等");
    }

    private static InterviewAgentService serviceReturning(String payload) {
        InterviewAgent agent = new InterviewAgent() {
            @Override
            public String nextQuestion(InterviewSession session, List<InterviewTurn> history) {
                return payload;
            }

            @Override
            public String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp) {
                return payload;
            }
        };
        return new InterviewAgentService(agent, new ObjectMapper(), null);
    }
}
