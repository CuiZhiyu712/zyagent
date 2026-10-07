package com.zyagent.agent.task;

import com.zyagent.agent.AgentMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTaskServiceTest {
    @Test
    void persistsLifecycleStateAndResult() {
        RecordingRepository repository = new RecordingRepository();
        AgentTaskService service = new AgentTaskService(repository, new RecordingStepRepository());

        AgentTask task = service.create("session-1", AgentMode.JOB_ANALYST, "分析岗位");
        task = service.start(task);
        task = service.succeed(task, "完成报告");

        assertEquals(AgentTaskState.SUCCEEDED, repository.last.state());
        assertEquals("完成报告", repository.last.resultText());
    }

    private static final class RecordingRepository implements AgentTaskRepositoryPort {
        private AgentTask last;

        @Override
        public void save(AgentTask task) {
            last = task;
        }

        @Override
        public Optional<AgentTask> findById(String ownerId, String taskId) {
            return Optional.ofNullable(last);
        }

        @Override
        public void markRunningTasksInterrupted() {
        }
    }

    private static final class RecordingStepRepository implements AgentTaskStepRepositoryPort {
        @Override
        public void save(AgentTaskStep step) {
        }
    }
}
