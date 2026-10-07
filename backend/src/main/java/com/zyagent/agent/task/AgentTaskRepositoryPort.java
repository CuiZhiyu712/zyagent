package com.zyagent.agent.task;

import java.util.Optional;

public interface AgentTaskRepositoryPort {
    void save(AgentTask task);

    Optional<AgentTask> findById(String ownerId, String taskId);

    /** 按幂等键查找已存在的任务，避免客户端重试导致重复执行。 */
    default Optional<AgentTask> findByIdempotencyKey(String ownerId, String idempotencyKey) {
        return Optional.empty();
    }

    void markRunningTasksInterrupted();
}
