package com.zyagent.modules.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.infrastructure.ai.AiChatService;
import com.zyagent.modules.agent.ChatMemorySnapshot;
import com.zyagent.modules.job.TestAssertions;
import com.zyagent.modules.job.JobDescriptionParser;
import com.zyagent.modules.agent.AgentRouteCategory;

public class ContextIntentClassifierTest {
    public static void run() {
        parsesStructuredContextRoute();
        rejectsNonJsonModelOutput();
        routesWithLlmResultBeforeKeywordFallback();
    }

    private static void parsesStructuredContextRoute() {
        ContextIntentClassifier classifier = new ContextIntentClassifier(
            new StubAiChatService("{\"intent\":\"continue_day\",\"skillId\":\"review_skill\",\"stage\":\"执行 Day1\",\"confidence\":0.93,\"contextRefs\":[\"14天计划\",\"Day1\"]}"),
            new ObjectMapper());

        ContextIntentResult result = classifier.classify("现在进行 Day1", new ChatMemorySnapshot(2, "上一轮计划"));

        TestAssertions.equals("review_skill", result.skillId(), "llm route skill");
        TestAssertions.equals("执行 Day1", result.stage(), "llm route stage");
        TestAssertions.isTrue(result.usable(), "llm route confidence");
    }

    private static void rejectsNonJsonModelOutput() {
        ContextIntentClassifier classifier = new ContextIntentClassifier(
            new StubAiChatService("这是一段普通回答，不是路由 JSON"), new ObjectMapper());

        ContextIntentResult result = classifier.classify("继续", ChatMemorySnapshot.empty());

        TestAssertions.isTrue(!result.usable(), "invalid llm output falls back");
    }

    private static void routesWithLlmResultBeforeKeywordFallback() {
        ContextIntentClassifier classifier = new ContextIntentClassifier(
            new StubAiChatService("{\"intent\":\"continue_interview\",\"skillId\":\"interview_skill\",\"stage\":\"回答评价\",\"confidence\":0.91,\"contextRefs\":[\"上一轮面试\"]}"),
            new ObjectMapper());
        SkillRouter router = new SkillRouter(new SkillCatalog(new JobDescriptionParser()), classifier);

        var decision = router.routeDecision(null, "我的回答里提到岗位匹配，请继续评价", new ChatMemorySnapshot(2, "上一轮面试"));

        TestAssertions.equals(AgentRouteCategory.INTERVIEW, decision.category(), "llm route beats keyword route");
        TestAssertions.isTrue(decision.reason().contains("LLM"), "llm route reason");
    }

    private static final class StubAiChatService extends AiChatService {
        private final String response;

        private StubAiChatService(String response) {
            super("test-key", null);
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return response;
        }
    }
}
