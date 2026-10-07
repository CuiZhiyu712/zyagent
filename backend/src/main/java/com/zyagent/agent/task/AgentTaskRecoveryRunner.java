package com.zyagent.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后把上次进程遗留的 {@code RUNNING} 任务标记为 {@code INTERRUPTED}。
 *
 * <p>不声称能从任意 LLM 调用中断点续跑；恢复只暴露可诊断状态，重试需用户显式发起。
 */
@Component
public class AgentTaskRecoveryRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentTaskRecoveryRunner.class);

    private final AgentTaskRepositoryPort repository;

    public AgentTaskRecoveryRunner(AgentTaskRepositoryPort repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedTasks() {
        try {
            repository.markRunningTasksInterrupted();
            log.info("启动恢复：遗留 RUNNING Agent task 已标记为 INTERRUPTED。");
        } catch (RuntimeException ex) {
            log.warn("启动恢复 Agent task 状态跳过：{}", ex.getMessage());
        }
    }
}
