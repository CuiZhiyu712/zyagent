package com.zyagent.agent.task;

import com.zyagent.agent.AgentMode;
import com.zyagent.common.CurrentUserProvider;
import com.zyagent.config.ZyagentProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskServicePersistenceTest {
    @Test
    void reportsMemoryModeWhenPersistenceFails() {
        AgentTaskService service = new AgentTaskService(new FailingRepository(), step -> { }, null, null);

        AgentTask task = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位");

        assertEquals("memory", service.persistenceMode(), "degraded persistence mode");
        assertEquals(AgentTaskState.PENDING, task.state(), "task still usable in memory");
    }

    @Test
    void strictModeSurfacesPersistenceFailure() {
        AgentTaskService service = new AgentTaskService(new FailingRepository(), step -> { }, properties(true), null);

        assertThrows(IllegalStateException.class,
            () -> service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位"));
    }

    @Test
    void usesOwnerFromCurrentUserProvider() {
        CurrentUserProvider provider = () -> "team-owner";
        AgentTaskService service = new AgentTaskService(new InMemoryRepository(), step -> { }, properties(false), provider);

        assertEquals("team-owner", service.currentOwnerId(), "owner from provider");
        AgentTask task = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位");
        assertEquals("team-owner", task.ownerId(), "created task carries configured owner");
    }

    @Test
    void reusesExistingTaskForSameIdempotencyKey() {
        AgentTaskService service = new AgentTaskService(new InMemoryRepository(), step -> { }, null, null);

        AgentTask first = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位", "key-1");
        AgentTask second = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位", "key-1");

        assertEquals(first.id(), second.id(), "same idempotency key reuses the task");
    }

    @Test
    void createsDistinctTasksWithoutIdempotencyKey() {
        AgentTaskService service = new AgentTaskService(new InMemoryRepository(), step -> { }, null, null);

        AgentTask first = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位");
        AgentTask second = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位");

        org.junit.jupiter.api.Assertions.assertNotEquals(first.id(), second.id(), "no key means distinct tasks");
    }

    private static ZyagentProperties properties(boolean strictPersistence) {
        ZyagentProperties.Task task = new ZyagentProperties.Task(
            "team-owner", 12, 8, 120_000L, 15_000L, 1, strictPersistence);
        return new ZyagentProperties(null, null, null, null, null, null, null, task, null, null, null, null);
    }

    private static final class FailingRepository implements AgentTaskRepositoryPort {
        @Override
        public void save(AgentTask task) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public Optional<AgentTask> findById(String ownerId, String taskId) {
            return Optional.empty();
        }

        @Override
        public void markRunningTasksInterrupted() {
        }
    }

    private static final class InMemoryRepository implements AgentTaskRepositoryPort {
        private final Map<String, AgentTask> byId = new LinkedHashMap<>();

        @Override
        public void save(AgentTask task) {
            byId.put(task.id(), task);
        }

        @Override
        public Optional<AgentTask> findById(String ownerId, String taskId) {
            return Optional.ofNullable(byId.get(taskId));
        }

        @Override
        public Optional<AgentTask> findByIdempotencyKey(String ownerId, String idempotencyKey) {
            return byId.values().stream()
                .filter(task -> idempotencyKey.equals(task.idempotencyKey()))
                .findFirst();
        }

        @Override
        public void markRunningTasksInterrupted() {
        }
    }
}
