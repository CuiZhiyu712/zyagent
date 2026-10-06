package com.zyagent.modules.interview;

import java.util.Map;
import java.util.Set;

/**
 * 单个面试轮次的状态机。
 *
 * <pre>
 * QUESTION_READY -> ANSWERED -> EVALUATED -> FOLLOW_UP_READY | NEXT_QUESTION_READY
 * </pre>
 *
 * 终态不可回退；所有写操作都必须校验当前状态。
 */
public enum InterviewTurnState {
    QUESTION_READY,
    ANSWERED,
    EVALUATED,
    FOLLOW_UP_READY,
    NEXT_QUESTION_READY;

    private static final Map<InterviewTurnState, Set<InterviewTurnState>> TRANSITIONS = Map.of(
        QUESTION_READY, Set.of(ANSWERED),
        ANSWERED, Set.of(EVALUATED),
        EVALUATED, Set.of(FOLLOW_UP_READY, NEXT_QUESTION_READY),
        FOLLOW_UP_READY, Set.of(),
        NEXT_QUESTION_READY, Set.of()
    );

    public boolean isTerminal() {
        return this == FOLLOW_UP_READY || this == NEXT_QUESTION_READY;
    }

    public boolean canTransitionTo(InterviewTurnState next) {
        return next != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public InterviewTurnState transitionTo(InterviewTurnState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("非法面试轮次状态迁移：" + this + " -> " + next);
        }
        return next;
    }
}
