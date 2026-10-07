package com.zyagent.interview;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 面试会话。状态机：{@code CREATED -> IN_PROGRESS -> COMPLETED | ABORTED | TIMED_OUT}。
 */
public record InterviewSession(
    String id,
    String ownerId,
    String jobId,
    String jdSnapshot,
    String interviewType,
    String difficulty,
    InterviewSessionState state,
    int currentTurn,
    int maxTurns,
    int maxFollowUps,
    String summary,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static final int DEFAULT_MAX_TURNS = 8;
    public static final int DEFAULT_MAX_FOLLOW_UPS = 1;

    public static InterviewSession create(String ownerId, String jobId, String jdSnapshot,
                                          String interviewType, String difficulty) {
        return create(ownerId, jobId, jdSnapshot, interviewType, difficulty, DEFAULT_MAX_TURNS, DEFAULT_MAX_FOLLOW_UPS);
    }

    public static InterviewSession create(String ownerId, String jobId, String jdSnapshot,
                                          String interviewType, String difficulty, int maxTurns, int maxFollowUps) {
        LocalDateTime now = LocalDateTime.now();
        return new InterviewSession(UUID.randomUUID().toString(), ownerId, jobId, jdSnapshot, interviewType,
            difficulty, InterviewSessionState.CREATED, 0, Math.max(1, maxTurns), Math.max(0, maxFollowUps),
            null, now, now);
    }

    public InterviewSession start() {
        if (state != InterviewSessionState.CREATED) {
            throw new IllegalStateException("面试会话不能开始：" + state);
        }
        return copy(InterviewSessionState.IN_PROGRESS, currentTurn, summary);
    }

    public InterviewSession nextTurn() {
        if (state != InterviewSessionState.IN_PROGRESS) {
            throw new IllegalStateException("面试会话不能继续答题：" + state);
        }
        if (currentTurn >= maxTurns) {
            throw new IllegalStateException("已达到最大轮次 " + maxTurns);
        }
        return copy(state, currentTurn + 1, summary);
    }

    public InterviewSession complete(String summary) {
        if (state == InterviewSessionState.COMPLETED) {
            return this;
        }
        if (state != InterviewSessionState.IN_PROGRESS) {
            throw new IllegalStateException("面试会话不能结束：" + state);
        }
        return copy(InterviewSessionState.COMPLETED, currentTurn, summary);
    }

    public InterviewSession abort() {
        if (state.isTerminal()) {
            return this;
        }
        return copy(InterviewSessionState.ABORTED, currentTurn, summary);
    }

    public boolean canContinue() {
        return state == InterviewSessionState.IN_PROGRESS && currentTurn < maxTurns;
    }

    private InterviewSession copy(InterviewSessionState nextState, int nextTurn, String nextSummary) {
        return new InterviewSession(id, ownerId, jobId, jdSnapshot, interviewType, difficulty, nextState,
            nextTurn, maxTurns, maxFollowUps, nextSummary, createdAt, LocalDateTime.now());
    }
}
