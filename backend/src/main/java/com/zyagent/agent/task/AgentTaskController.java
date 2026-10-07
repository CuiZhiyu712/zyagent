package com.zyagent.agent.task;

import com.zyagent.agent.AgentOrchestrator;
import com.zyagent.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {
    private final AgentTaskService agentTaskService;
    private final AgentTaskStepRepositoryPort stepRepository;
    private final AgentOrchestrator orchestrator;

    public AgentTaskController(
        AgentTaskService agentTaskService,
        AgentTaskStepRepositoryPort stepRepository,
        AgentOrchestrator orchestrator
    ) {
        this.agentTaskService = agentTaskService;
        this.stepRepository = stepRepository;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AgentTask> get(@PathVariable String taskId) {
        return ApiResponse.ok(requireTask(taskId));
    }

    @GetMapping("/{taskId}/steps")
    public ApiResponse<List<AgentTaskStep>> steps(@PathVariable String taskId) {
        requireTask(taskId);
        return ApiResponse.ok(stepRepository.findByTaskId(taskId));
    }

    /** 显式重试一个已结束的任务：创建后继任务并整体重跑，不尝试从中断点续跑。 */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<AgentTask> retry(@PathVariable String taskId) {
        AgentTask task = requireTask(taskId);
        if (!isRetryable(task.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务未结束或已成功，无法重试");
        }
        return ApiResponse.ok(orchestrator.retry(task));
    }

    private AgentTask requireTask(String taskId) {
        return agentTaskService.find(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private static boolean isRetryable(AgentTaskState state) {
        return state == AgentTaskState.FAILED
            || state == AgentTaskState.TIMED_OUT
            || state == AgentTaskState.INTERRUPTED
            || state == AgentTaskState.CANCELLED;
    }
}
