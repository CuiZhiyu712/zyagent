package com.zyagent.modules.agent.task;

import com.zyagent.modules.agent.AgentPlanStep;

/**
 * Agent 编排边界上的步骤生命周期端口。
 *
 * <p>编排器只负责在步骤开始/结束时回调对应的 {@link AgentPlanStep} 快照，不直接写 JDBC；
 * 持久化与 SSE 推送由实现方决定。同一个步骤的 {@code stepId} 在两次回调间保持不变。
 */
public interface AgentExecutionListener {
    default void onStepStarted(String taskId, int stepNo, AgentPlanStep step) {
    }

    default void onStepFinished(String taskId, int stepNo, AgentPlanStep step) {
    }

    AgentExecutionListener NOOP = new AgentExecutionListener() {
    };
}
