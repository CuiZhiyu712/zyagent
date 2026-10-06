package com.zyagent.modules.agent.task;

import java.util.EnumSet;
import java.util.Map;

public enum AgentTaskState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    INTERRUPTED;

    private static final Map<AgentTaskState, EnumSet<AgentTaskState>> TRANSITIONS = Map.of(
        PENDING, EnumSet.of(RUNNING, CANCELLED),
        RUNNING, EnumSet.of(SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, INTERRUPTED),
        INTERRUPTED, EnumSet.of(PENDING, CANCELLED),
        SUCCEEDED, EnumSet.noneOf(AgentTaskState.class),
        FAILED, EnumSet.noneOf(AgentTaskState.class),
        TIMED_OUT, EnumSet.noneOf(AgentTaskState.class),
        CANCELLED, EnumSet.noneOf(AgentTaskState.class)
    );

    public AgentTaskState transitionTo(AgentTaskState next) {
        if (next == null || !TRANSITIONS.get(this).contains(next)) {
            throw new IllegalStateException("非法任务状态迁移：" + this + " -> " + next);
        }
        return next;
    }

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == TIMED_OUT || this == CANCELLED;
    }
}
