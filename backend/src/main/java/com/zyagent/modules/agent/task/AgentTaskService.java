package com.zyagent.modules.agent.task;

import com.zyagent.modules.agent.AgentMode;
import com.zyagent.modules.agent.AgentPlanStep;
import com.zyagent.common.CurrentUserProvider;
import com.zyagent.infrastructure.config.ZyagentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentTaskService {
    public static final String DEFAULT_OWNER = "local-user";
    private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);

    private final AgentTaskRepositoryPort repository;
    private final AgentTaskStepRepositoryPort stepRepository;
    private final String ownerId;
    private final boolean strictPersistence;
    private final AtomicBoolean persistenceFailed = new AtomicBoolean(false);

    public AgentTaskService(AgentTaskRepositoryPort repository, AgentTaskStepRepositoryPort stepRepository) {
        this(repository, stepRepository, null, null);
    }

    @Autowired
    public AgentTaskService(
        AgentTaskRepositoryPort repository,
        AgentTaskStepRepositoryPort stepRepository,
        ZyagentProperties properties,
        CurrentUserProvider currentUserProvider
    ) {
        this.repository = repository;
        this.stepRepository = stepRepository;
        ZyagentProperties.Task task = properties == null ? null : properties.task();
        this.strictPersistence = task != null && task.strictPersistence();
        if (currentUserProvider != null && currentUserProvider.currentUserId() != null && !currentUserProvider.currentUserId().isBlank()) {
            this.ownerId = currentUserProvider.currentUserId();
        } else if (task != null && task.ownerId() != null && !task.ownerId().isBlank()) {
            this.ownerId = task.ownerId();
        } else {
            this.ownerId = DEFAULT_OWNER;
        }
    }

    /** 当前任务的归属者。无认证时取配置默认值，未来接入认证后由 {@link CurrentUserProvider} 决定。 */
    public String currentOwnerId() {
        return ownerId;
    }

    /** {@code mysql} 表示任务已落库，{@code memory} 表示数据库不可用、仅本地演示降级。 */
    public String persistenceMode() {
        return persistenceFailed.get() ? "memory" : "mysql";
    }

    public AgentTask create(String sessionId, AgentMode mode, String requestText) {
        return create(sessionId, mode, requestText, null);
    }

    /**
     * 创建任务；若提供了幂等键且已存在同键任务，则直接返回已存在的任务，避免重复执行。
     */
    public AgentTask create(String sessionId, AgentMode mode, String requestText, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<AgentTask> existing = findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        LocalDateTime now = LocalDateTime.now();
        AgentTask task = new AgentTask(UUID.randomUUID().toString(), ownerId, sessionId, mode,
            AgentTaskState.PENDING, requestText, null, null, null, now, null, null, now,
            blankToNull(idempotencyKey));
        persist(task);
        return task;
    }

    public Optional<AgentTask> findByIdempotencyKey(String idempotencyKey) {
        try {
            return repository.findByIdempotencyKey(ownerId, idempotencyKey);
        } catch (RuntimeException ex) {
            log.warn("按幂等键查询 Agent task 失败：{}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AgentTask> find(String taskId) {
        try {
            return repository.findById(ownerId, taskId);
        } catch (RuntimeException ex) {
            log.warn("查询 Agent task {} 失败：{}", taskId, ex.getMessage());
            return Optional.empty();
        }
    }

    public AgentTask start(AgentTask task) {
        AgentTask updated = task.start();
        persist(updated);
        return updated;
    }

    public AgentTask succeed(AgentTask task, String result) {
        AgentTask updated = task.succeed(result);
        persist(updated);
        return updated;
    }

    public AgentTask fail(AgentTask task, String code, String message) {
        AgentTask updated = task.fail(code, message);
        persist(updated);
        return updated;
    }

    public void saveStep(String taskId, int stepNo, AgentPlanStep step) {
        persistStep(AgentTaskStep.from(taskId, stepNo, step));
    }

    public void saveSteps(String taskId, List<AgentPlanStep> steps) {
        for (int i = 0; i < steps.size(); i++) {
            saveStep(taskId, i + 1, steps.get(i));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void persist(AgentTask task) {
        doPersist("task " + task.id(), () -> repository.save(task));
    }

    private void persistStep(AgentTaskStep step) {
        doPersist("step " + step.stepNo() + " of task " + step.taskId(), () -> stepRepository.save(step));
    }

    private void doPersist(String subject, Runnable action) {
        try {
            action.run();
            persistenceFailed.set(false);
        } catch (RuntimeException ex) {
            persistenceFailed.set(true);
            if (strictPersistence) {
                throw new IllegalStateException("Agent 任务持久化失败（严格模式）：" + subject, ex);
            }
            log.warn("Agent 任务持久化失败，降级为本地演示模式({}): {}", subject, ex.getMessage());
        }
    }
}
