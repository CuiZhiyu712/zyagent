package com.zyagent.agent.task;

/**
 * SSE {@code status} 事件载荷：把任务终态与持久化模式明确告知前端。
 */
public record AgentTaskStatusUpdate(
    String taskId,
    String state,
    String errorCode,
    String errorMessage,
    String persistence
) {
    public static AgentTaskStatusUpdate from(AgentTask task, String persistence) {
        return new AgentTaskStatusUpdate(task.id(), task.state().name(), task.errorCode(), task.errorMessage(), persistence);
    }
}
