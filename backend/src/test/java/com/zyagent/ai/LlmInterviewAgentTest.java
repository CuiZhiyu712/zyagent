package com.zyagent.ai;

import com.zyagent.interview.InterviewSession;
import com.zyagent.interview.InterviewTurn;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmInterviewAgentTest {
    @Test
    void composesQuestionPromptWithJobDescriptionAndPriorTurns() {
        RecordingChatClient client = new RecordingChatClient();
        LlmInterviewAgent agent = agent(client, "test-key");
        InterviewSession session = session();
        InterviewTurn previousTurn = InterviewTurn.question(session.id(), 1, "上一轮问题：Redis 缓存如何设计？")
            .answered("上一轮回答：项目上线后缓存命中率提升了 32%。", "answer-1");

        assertEquals("{\"question\":\"下一题\",\"focus\":\"项目深挖\"}",
            agent.nextQuestion(session, List.of(previousTurn)));

        assertEquals(1, client.calls);
        assertTrue(client.systemPrompt.contains("只输出 JSON"));
        assertTrue(client.systemPrompt.contains("{\"question\":\"...\",\"focus\":\"...\"}"));
        assertTrue(client.userPrompt.contains("JD 唯一内容：面向高并发订单系统的后端岗位"));
        assertTrue(client.userPrompt.contains("上一轮问题：Redis 缓存如何设计？"));
        assertTrue(client.userPrompt.contains("上一轮回答：项目上线后缓存命中率提升了 32%。"));
        assertTrue(client.userPrompt.contains("第 1 轮"), "prior turn number is included in history");
    }

    @Test
    void composesEvaluationPromptWithCompleteAnswerFollowUpAndExactEvidenceRules() {
        RecordingChatClient client = new RecordingChatClient();
        LlmInterviewAgent agent = agent(client, "test-key");
        InterviewSession session = session();
        String answer = "我负责订单服务的 Redis 缓存改造，项目上线后 P95 延迟从 120ms 降至 80ms，"
            + "并通过压测验证峰值流量下没有重复扣款。以上是完整回答结尾。";

        assertEquals("{\"question\":\"下一题\",\"focus\":\"项目深挖\"}",
            agent.evaluateAnswer(session, "请说明你的取舍", answer, true));

        assertEquals(1, client.calls);
        assertTrue(client.userPrompt.contains("JD 唯一内容：面向高并发订单系统的后端岗位"));
        assertTrue(client.userPrompt.contains("请说明你的取舍"));
        assertTrue(client.userPrompt.contains(answer), "the entire answer is included");
        assertTrue(client.userPrompt.contains("是否允许追问：true"));
        assertTrue(client.systemPrompt.contains("证据必须从回答原文逐字摘录"));
        assertTrue(client.systemPrompt.contains("不得改写、概括或补造证据"));
    }

    @Test
    void reportsLlmMetadataAndCredentialAvailability() {
        LlmInterviewAgent availableAgent = agent(new RecordingChatClient(), "test-key");
        LlmInterviewAgent unavailableAgent = agent(new RecordingChatClient(), " \t ");

        assertEquals("llm", availableAgent.provider());
        assertEquals("DeepSeek AI 面试官", availableAgent.label());
        assertTrue(availableAgent.available());
        assertFalse(unavailableAgent.available());
    }

    @Test
    void missingCredentialsFailBeforeCallingClient() {
        RecordingChatClient client = new RecordingChatClient();
        LlmInterviewAgent agent = agent(client, "");

        assertFalse(agent.available());
        assertThrows(IllegalStateException.class, () -> agent.nextQuestion(session(), List.of()));
        assertThrows(IllegalStateException.class,
            () -> agent.evaluateAnswer(session(), "问题", "完整回答", true));
        assertEquals(0, client.calls);
    }

    @Test
    void propagatesClientFailuresWithoutFallback() {
        IllegalStateException failure = new IllegalStateException("client unavailable");
        RecordingChatClient client = new RecordingChatClient(failure);
        LlmInterviewAgent agent = agent(client, "test-key");

        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> agent.nextQuestion(session(), List.of())));
        assertEquals(1, client.calls);
    }

    private static LlmInterviewAgent agent(RecordingChatClient client, String apiKey) {
        return new LlmInterviewAgent(
            client,
            apiKey,
            new ClassPathResource("prompts/interview-question-system.st"),
            new ClassPathResource("prompts/interview-question-user.st"),
            new ClassPathResource("prompts/interview-evaluation-system.st"),
            new ClassPathResource("prompts/interview-evaluation-user.st"));
    }

    private static InterviewSession session() {
        return InterviewSession.create("owner-1", "job-1", "JD 唯一内容：面向高并发订单系统的后端岗位",
            "项目深挖", "中等");
    }

    private static final class RecordingChatClient implements AiChatClient {
        private final RuntimeException failure;
        private int calls;
        private String systemPrompt;
        private String userPrompt;

        private RecordingChatClient() {
            this(null);
        }

        private RecordingChatClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            calls++;
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            if (failure != null) {
                throw failure;
            }
            return "{\"question\":\"下一题\",\"focus\":\"项目深挖\"}";
        }

        @Override
        public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
            throw new AssertionError("interview agent should use complete, not stream");
        }
    }
}
