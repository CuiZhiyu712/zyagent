package com.zyagent.agent.task;

import java.util.List;

public interface AgentTaskStepRepositoryPort {
    void save(AgentTaskStep step);

    default List<AgentTaskStep> findByTaskId(String taskId) {
        return List.of();
    }
}
