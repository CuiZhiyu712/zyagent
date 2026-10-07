package com.zyagent.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAgentServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SCORE_FIELDS = List.of(
        "technicalCorrectness", "completeness", "projectEvidence", "expressionStructure");
    private static final String VALID_EVALUATION_JSON =
        "{\"technicalCorrectness\":4,\"completeness\":3,\"projectEvidence\":2,"
            + "\"expressionStructure\":5,\"explanations\":[\"ok\"],\"evidence\":[],"
            + "\"followUp\":false,\"followUpQuestion\":null}";

    @Test
    void exposesProviderMetadataForTestAndRuleBasedAgents() {
        InterviewAgent testAgent = agentReturning("{}");

        assertEquals("test", testAgent.provider());
        assertTrue(testAgent.available());
        assertEquals("测试面试官", testAgent.label());

        RuleBasedInterviewAgent ruleAgent = new RuleBasedInterviewAgent(new ObjectMapper());
        assertEquals("rule_demo", ruleAgent.provider());
        assertEquals("规则演示模式", ruleAgent.label());
        assertTrue(ruleAgent.available());
    }

    @Test
    void parsesValidEvaluation() throws Exception {
        InterviewAgentService service = serviceReturning(
            withJsonField(VALID_EVALUATION_JSON, "evidence", "[\"Redis 缓存\"]"));

        AnswerAssessment assessment = service.assess(session(), "q", "我使用 Redis 缓存热点数据", true);

        assertTrue(assessment.usable());
        assertEquals(4, assessment.evaluation().technicalCorrectness());
        assertEquals(2, assessment.evaluation().projectEvidence());
        assertEquals(List.of("Redis 缓存"), assessment.evaluation().evidenceRefs());
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
    void rejectsMissingAnyScore() throws Exception {
        for (String scoreField : SCORE_FIELDS) {
            assertUnusable(withoutJsonField(VALID_EVALUATION_JSON, scoreField), "candidate answer", "评分");
        }
    }

    @Test
    void rejectsNonIntegralScore() throws Exception {
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "technicalCorrectness", "\"4\""),
            "candidate answer", "评分");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "completeness", "3.5"),
            "candidate answer", "评分");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "projectEvidence", "true"),
            "candidate answer", "评分");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "expressionStructure", "false"),
            "candidate answer", "评分");
    }

    @Test
    void rejectsScoreOutsideZeroToFive() throws Exception {
        for (String scoreField : SCORE_FIELDS) {
            assertUnusable(withJsonField(VALID_EVALUATION_JSON, scoreField, "-1"), "candidate answer", "评分");
            assertUnusable(withJsonField(VALID_EVALUATION_JSON, scoreField, "6"), "candidate answer", "评分");
        }
    }

    @Test
    void acceptsSingleOuterJsonFence() {
        String fenced = "```json\n" + VALID_EVALUATION_JSON + "\n```";

        AnswerAssessment assessment = assessmentFor(fenced, "candidate answer");

        assertTrue(assessment.usable());
        assertTrue(assessment.evaluation().usable());
    }

    @Test
    void rejectsMultipleOrTrailingJsonFences() {
        String nestedFence = "```json\n```json\n" + VALID_EVALUATION_JSON + "\n```\n```";
        String repeatedFence = "```json\n" + VALID_EVALUATION_JSON + "\n```\n```json\n{}\n```";

        assertUnusable(nestedFence, "candidate answer", "JSON");
        assertUnusable(repeatedFence, "candidate answer", "JSON");
    }

    @Test
    void rejectsTrailingJsonTokens() {
        assertUnusable(VALID_EVALUATION_JSON + " {}", "candidate answer", "JSON");
    }

    @Test
    void rejectsEvidenceNotQuotedFromAnswer() throws Exception {
        String payload = withJsonField(VALID_EVALUATION_JSON, "evidence", "[\"QPS 提升 80%\"]");

        assertUnusable(payload, "我使用 Redis 缓存热点数据", "证据");
    }

    @Test
    void rejectsEvidenceWithCaseOrWhitespaceDrift() throws Exception {
        String answer = "我使用 Redis 缓存热点数据";
        for (String evidence : List.of("redis 缓存", "Redis  缓存")) {
            String payload = withJsonField(VALID_EVALUATION_JSON, "evidence", JSON.writeValueAsString(List.of(evidence)));
            assertUnusable(payload, answer, "证据");
        }
    }

    @Test
    void rejectsNonArrayExplanations() throws Exception {
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "explanations", "\"ok\""),
            "candidate answer", "解释");
    }

    @Test
    void rejectsNonArrayEvidence() throws Exception {
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "evidence", "\"Redis 缓存\""),
            "我使用 Redis 缓存热点数据", "证据");
    }

    @Test
    void rejectsNonTextArrayItemsForExplanationsAndEvidence() throws Exception {
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "explanations", "[\"ok\", 5]"),
            "我使用 Redis 缓存热点数据", "解释");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "evidence", "[\"Redis\", false]"),
            "我使用 Redis 缓存热点数据", "证据");
    }

    @Test
    void rejectsFollowUpWithoutQuestion() throws Exception {
        String payload = withJsonField(VALID_EVALUATION_JSON, "followUp", "true");
        assertUnusable(payload, "candidate answer", "追问");

        String blankQuestion = withJsonField(payload, "followUpQuestion", "\"  \"");
        assertUnusable(blankQuestion, "candidate answer", "追问");
    }

    @Test
    void rejectsMissingOrNonBooleanFollowUp() throws Exception {
        assertUnusable(withoutJsonField(VALID_EVALUATION_JSON, "followUp"), "candidate answer", "追问");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "followUp", "\"true\""),
            "candidate answer", "追问");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "followUp", "1"),
            "candidate answer", "追问");
        assertUnusable(withJsonField(VALID_EVALUATION_JSON, "followUp", "null"),
            "candidate answer", "追问");
    }

    @Test
    void rejectsMissingOrNonTextFollowUpQuestionEvenWhenFollowUpIsFalse() throws Exception {
        assertUnusable(withoutJsonField(VALID_EVALUATION_JSON, "followUpQuestion"),
            "candidate answer", "追问");
        for (String question : List.of("123", "{\"question\":\"more\"}", "[\"more\"]")) {
            assertUnusable(withJsonField(VALID_EVALUATION_JSON, "followUpQuestion", question),
                "candidate answer", "追问");
        }
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

    @Test
    void rejectsNonTextualOrMissingQuestionValues() {
        for (String output : List.of(
            "{\"question\":7}",
            "{\"question\":true}",
            "{\"question\":{\"text\":\"not a question node\"}}",
            "{\"question\":[\"not a question node\"]}",
            "{\"question\":null}",
            "{\"question\":\"  \"}",
            "{}"
        )) {
            QuestionPlan plan = serviceReturning(output).nextQuestion(session(), List.of());

            assertFalse(plan.usable(), "question node must be a nonblank JSON string: " + output);
            assertTrue(plan.note().contains("兜底"), "invalid question falls back safely");
        }
    }

    private static InterviewSession session() {
        return InterviewSession.create("owner-1", "job-1", null, "项目深挖", "中等");
    }

    private static InterviewAgentService serviceReturning(String payload) {
        return new InterviewAgentService(agentReturning(payload), new ObjectMapper(), null);
    }

    private static AnswerAssessment assessmentFor(String payload, String answer) {
        return serviceReturning(payload).assess(session(), "q", answer, true);
    }

    private static void assertUnusable(String payload, String answer, String reasonCategory) {
        AnswerAssessment assessment = assessmentFor(payload, answer);

        assertFalse(assessment.usable(), "invalid evaluation must not be usable");
        assertFalse(assessment.evaluation().usable(), "invalid evaluation value must be flagged unusable");
        assertTrue(assessment.note().contains(reasonCategory), "fallback note retains reason category");
    }

    private static String withJsonField(String payload, String field, String jsonValue) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(payload);
        root.set(field, JSON.readTree(jsonValue));
        return JSON.writeValueAsString(root);
    }

    private static String withoutJsonField(String payload, String field) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(payload);
        root.remove(field);
        return JSON.writeValueAsString(root);
    }

    private static InterviewAgent agentReturning(String payload) {
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
        return agent;
    }
}
