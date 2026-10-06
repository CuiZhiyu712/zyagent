package com.zyagent.modules.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewServiceTest {
    @Test
    void startCreatesSessionWithFirstQuestion() {
        FakeRepository repository = new FakeRepository();
        InterviewService service = new InterviewService(repository, agentService(new StubAgent()));

        InterviewSession session = service.start("job-1", "JD 快照", "项目深挖", "中等");

        assertEquals(InterviewSessionState.IN_PROGRESS, session.state());
        assertEquals(1, repository.turns(session.id()).size());
        assertEquals(InterviewTurnState.QUESTION_READY, repository.turns(session.id()).get(0).state());
    }

    @Test
    void submitTurnEvaluatesAndAdvancesToNextQuestion() {
        FakeRepository repository = new FakeRepository();
        InterviewService service = new InterviewService(repository, agentService(new StubAgent()));
        InterviewSession session = service.start("job-1", null, "项目深挖", "中等");

        InterviewTurn turn = service.submitTurn(session.id(), "我使用 Redis 做了缓存优化，QPS 提升 30%", "req-1");

        assertEquals(InterviewTurnState.NEXT_QUESTION_READY, turn.state());
        assertTrue(turn.evaluation().usable(), "evaluation usable");
        assertEquals(2, repository.turns(session.id()).size(), "next question created");
        assertEquals(1, service.get(session.id()).currentTurn());
    }

    @Test
    void duplicateRequestIdIsIdempotent() {
        FakeRepository repository = new FakeRepository();
        InterviewService service = new InterviewService(repository, agentService(new StubAgent()));
        InterviewSession session = service.start("job-1", null, "项目深挖", "中等");

        InterviewTurn first = service.submitTurn(session.id(), "第一次回答", "req-1");
        InterviewTurn replay = service.submitTurn(session.id(), "重复提交", "req-1");

        assertEquals(first.id(), replay.id(), "same request id returns the same turn");
        assertEquals("第一次回答", replay.answer(), "answer not overwritten");
        assertEquals(2, repository.turns(session.id()).size(), "no extra turn created");
    }

    @Test
    void rejectsAnswerWhenSessionCompleted() {
        FakeRepository repository = new FakeRepository();
        InterviewService service = new InterviewService(repository, agentService(new StubAgent()));
        InterviewSession session = service.start("job-1", null, "项目深挖", "中等");
        service.complete(session.id());

        assertThrows(IllegalStateException.class, () -> service.submitTurn(session.id(), "太晚了", null));
    }

    @Test
    void completeProducesSummary() {
        FakeRepository repository = new FakeRepository();
        InterviewService service = new InterviewService(repository, agentService(new StubAgent()));
        InterviewSession session = service.start("job-1", null, "项目深挖", "中等");
        service.submitTurn(session.id(), "我使用 Redis 做了缓存优化，QPS 提升 30%", "req-1");

        InterviewSession completed = service.complete(session.id());

        assertEquals(InterviewSessionState.COMPLETED, completed.state());
        assertTrue(completed.summary().contains("平均分"), "summary contains average score");
    }

    private static InterviewAgentService agentService(InterviewAgent agent) {
        return new InterviewAgentService(agent, new ObjectMapper(), null);
    }

    /** 返回固定合法 JSON 的桩实现。 */
    private static final class StubAgent implements InterviewAgent {
        @Override
        public String nextQuestion(InterviewSession session, List<InterviewTurn> history) {
            return "{\"question\":\"请说明缓存一致性方案\",\"focus\":\"" + session.interviewType() + "\"}";
        }

        @Override
        public String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp) {
            return "{\"technicalCorrectness\":4,\"completeness\":4,\"projectEvidence\":4,"
                + "\"expressionStructure\":4,\"explanations\":[\"结构清晰\"],\"evidence\":[],"
                + "\"followUp\":false,\"followUpQuestion\":null}";
        }
    }

    private static final class FakeRepository implements InterviewRepositoryPort {
        private final Map<String, InterviewSession> sessions = new LinkedHashMap<>();
        private final Map<String, List<InterviewTurn>> turns = new LinkedHashMap<>();

        @Override
        public void saveSession(InterviewSession session) {
            sessions.put(session.id(), session);
        }

        @Override
        public Optional<InterviewSession> findSession(String id) {
            return Optional.ofNullable(sessions.get(id));
        }

        @Override
        public void saveTurn(InterviewTurn turn) {
            List<InterviewTurn> list = turns.computeIfAbsent(turn.sessionId(), ignored -> new ArrayList<>());
            list.removeIf(existing -> existing.turnNo() == turn.turnNo());
            list.add(turn);
        }

        @Override
        public Optional<InterviewTurn> findTurn(String sessionId, int turnNo) {
            return turns(sessionId).stream().filter(turn -> turn.turnNo() == turnNo).findFirst();
        }

        @Override
        public Optional<InterviewTurn> findTurnByRequestId(String sessionId, String requestId) {
            return turns(sessionId).stream()
                .filter(turn -> requestId != null && requestId.equals(turn.requestId()))
                .findFirst();
        }

        @Override
        public List<InterviewTurn> findTurns(String sessionId) {
            return turns(sessionId);
        }

        private List<InterviewTurn> turns(String sessionId) {
            return turns.getOrDefault(sessionId, List.of());
        }
    }
}
