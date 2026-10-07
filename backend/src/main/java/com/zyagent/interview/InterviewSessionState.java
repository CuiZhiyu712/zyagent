package com.zyagent.interview;

import java.util.Map;
import java.util.Set;

/**
 * 面试会话状态机：{@code CREATED -> IN_PROGRESS -> COMPLETED | ABORTED | TIMED_OUT}。
 */
public enum InterviewSessionState {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    ABORTED,
    TIMED_OUT;

    private static final Map<InterviewSessionState, Set<InterviewSessionState>> TRANSITIONS = Map.of(
        CREATED, Set.of(IN_PROGRESS, ABORTED),
        IN_PROGRESS, Set.of(COMPLETED, ABORTED, TIMED_OUT),
        COMPLETED, Set.of(),
        ABORTED, Set.of(),
        TIMED_OUT, Set.of()
    );

    public boolean isTerminal() {
        return this == COMPLETED || this == ABORTED || this == TIMED_OUT;
    }

    public boolean canTransitionTo(InterviewSessionState next) {
        return next != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public InterviewSessionState transitionTo(InterviewSessionState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("非法面试会话状态迁移：" + this + " -> " + next);
        }
        return next;
    }
}
