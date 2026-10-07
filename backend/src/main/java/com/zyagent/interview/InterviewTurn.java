package com.zyagent.interview;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 一轮面试：问题、回答、结构化评价与追问。
 *
 * <p>状态迁移由 {@link InterviewTurnState} 校验；重复提交通过 {@code requestId} 幂等识别。
 */
public record InterviewTurn(
    String id,
    String sessionId,
    int turnNo,
    InterviewTurnState state,
    String question,
    String answer,
    InterviewEvaluation evaluation,
    String followUpQuestion,
    String requestId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static InterviewTurn question(String sessionId, int turnNo, String question) {
        LocalDateTime now = LocalDateTime.now();
        return new InterviewTurn(UUID.randomUUID().toString(), sessionId, turnNo, InterviewTurnState.QUESTION_READY,
            question, null, null, null, null, now, now);
    }

    public InterviewTurn answered(String answer, String requestId) {
        return new InterviewTurn(id, sessionId, turnNo, state.transitionTo(InterviewTurnState.ANSWERED),
            question, answer, evaluation, followUpQuestion,
            requestId == null || requestId.isBlank() ? this.requestId : requestId,
            createdAt, LocalDateTime.now());
    }

    /** 写入评价，并根据是否追问落到 FOLLOW_UP_READY 或 NEXT_QUESTION_READY。 */
    public InterviewTurn evaluated(InterviewEvaluation evaluation, String followUpQuestion) {
        InterviewTurnState evaluated = state.transitionTo(InterviewTurnState.EVALUATED);
        InterviewTurnState terminal = followUpQuestion == null || followUpQuestion.isBlank()
            ? InterviewTurnState.NEXT_QUESTION_READY
            : InterviewTurnState.FOLLOW_UP_READY;
        evaluated.transitionTo(terminal);
        return new InterviewTurn(id, sessionId, turnNo, terminal, question, answer, evaluation,
            followUpQuestion, requestId, createdAt, LocalDateTime.now());
    }

    public boolean hasAnswer() {
        return answer != null && !answer.isBlank();
    }

    public boolean hasFollowUp() {
        return followUpQuestion != null && !followUpQuestion.isBlank();
    }
}
