package com.zyagent.agent.task;

/**
 * 单次 Agent task 的执行上限，防止无限循环与失控的工具调用。
 */
public record AgentTaskLimits(
    int maxPlanSteps,
    int maxToolCalls,
    long taskTimeoutMs,
    long toolTimeoutMs,
    int maxRetries
) {
    public AgentTaskLimits {
        if (maxPlanSteps <= 0) {
            throw new IllegalArgumentException("maxPlanSteps 必须为正数");
        }
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls 必须为正数");
        }
        if (taskTimeoutMs <= 0) {
            throw new IllegalArgumentException("taskTimeoutMs 必须为正数");
        }
        if (toolTimeoutMs <= 0) {
            throw new IllegalArgumentException("toolTimeoutMs 必须为正数");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
    }

    public static AgentTaskLimits defaults() {
        return new AgentTaskLimits(12, 8, 120_000L, 15_000L, 1);
    }
}
