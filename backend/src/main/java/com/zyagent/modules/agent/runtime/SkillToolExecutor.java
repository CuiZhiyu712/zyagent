package com.zyagent.modules.agent.runtime;

import com.zyagent.modules.agent.AgentPlanStep;
import com.zyagent.modules.agent.skill.IntentSignals;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.task.AgentExecutionListener;
import com.zyagent.modules.agent.task.AgentExecutionPolicy;
import com.zyagent.modules.agent.task.AgentTaskLimits;
import com.zyagent.modules.agent.tool.AgentToolService;
import com.zyagent.modules.agent.tool.ToolResult;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SkillToolExecutor {
    private final AgentToolService tools;
    private final AgentExecutionPolicy policy;
    private final int replanMaxAttempts;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "zyagent-tool-executor");
        thread.setDaemon(true);
        return thread;
    });

    public SkillToolExecutor(AgentToolService tools, com.zyagent.infrastructure.config.ZyagentProperties properties) {
        this.tools = tools;
        var task = properties.task();
        this.policy = new AgentExecutionPolicy(task == null ? AgentTaskLimits.defaults()
            : new AgentTaskLimits(task.maxPlanSteps(), task.maxToolCalls(), task.timeoutMs(), task.toolTimeoutMs(), task.maxRetries()));
        this.replanMaxAttempts = properties.agent() == null ? 1 : Math.max(0, properties.agent().replanMaxAttempts());
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }

    public Execution execute(SkillDefinition skill, String message, String taskId, AgentExecutionListener listener) {
        List<ToolResult> results = new ArrayList<>();
        List<AgentPlanStep> steps = new ArrayList<>();
        int retryCount = 0;
        for (int index = 0; index < skill.planSteps().size(); index++) {
            int stepNo = index + 1;
            String toolName = index < skill.toolNames().size() ? skill.toolNames().get(index) : null;
            AgentPlanStep planned = AgentPlanStep.planned(skill.planSteps().get(index), toolName).withInputSummary(summarize(message));
            if (!policy.withinPlanSteps(index)) {
                steps.add(finish(listener, taskId, stepNo, planned.skipped("超过最大计划步骤上限 " + policy.limits().maxPlanSteps()))); continue;
            }
            if (toolName == null || "match_resume_job".equals(toolName)) {
                steps.add(finish(listener, taskId, stepNo, planned.skipped("该步骤由最终回答综合完成"))); continue;
            }
            if (!IntentSignals.hasContentForTool(toolName, message)) {
                steps.add(finish(listener, taskId, stepNo, planned.skipped("本轮消息不是该工具的有效输入，已跳过"))); continue;
            }
            if (!policy.withinToolCalls(results.size())) {
                steps.add(finish(listener, taskId, stepNo, planned.skipped("超过最大工具调用上限 " + policy.limits().maxToolCalls()))); continue;
            }
            listener.onStepStarted(taskId, stepNo, planned.running());
            long started = System.nanoTime();
            int attempt = 0;
            ToolResult result;
            do { attempt++; result = executeTool(toolName, message); }
            while (!result.success() && policy.canRetry(toolName, attempt));
            results.add(result);
            if (attempt > 1) retryCount += attempt - 1;
            long duration = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            String output = result.success() ? summarize(String.valueOf(result.output())) : result.errorMessage();
            AgentPlanStep attempted = planned.withAttempt(attempt);
            AgentPlanStep finished = result.success() ? attempted.withOutputSummary(output).success(duration)
                : attempted.withOutputSummary(output).failed(result.errorMessage(), duration);
            steps.add(finish(listener, taskId, stepNo, finished));
            if (!result.success() && replanMaxAttempts > 0) {
                retryCount++;
                AgentPlanStep replanned = AgentPlanStep.planned("根据工具失败结果调整回答策略", null).replanned(result.errorMessage());
                steps.add(replanned); listener.onStepFinished(taskId, steps.size(), replanned);
            }
        }
        return new Execution(steps, results, retryCount);
    }

    private AgentPlanStep finish(AgentExecutionListener listener, String taskId, int stepNo, AgentPlanStep step) {
        listener.onStepFinished(taskId, stepNo, step); return step;
    }

    private ToolResult executeTool(String name, String message) {
        Future<Object> future;
        try { future = executor.submit(() -> tools.execute(name, inputFor(name, message))); }
        catch (RejectedExecutionException ex) { return ToolResult.failure(name, "工具执行器不可用"); }
        try {
            Object output = future.get(policy.limits().toolTimeoutMs(), TimeUnit.MILLISECONDS);
            return ToolResult.success(name, output, output);
        } catch (TimeoutException ex) {
            future.cancel(true); return ToolResult.failure(name, "工具执行超时（" + policy.limits().toolTimeoutMs() + "ms）");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); return ToolResult.failure(name, "工具执行被中断");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause(); return ToolResult.failure(name, cause.getMessage());
        }
    }

    private Map<String, Object> inputFor(String name, String message) {
        return switch (name) {
            case "parse_job_description" -> Map.of("rawText", message);
            case "search_personal_knowledge" -> Map.of("query", message);
            case "generate_interview_questions" -> Map.of("jobText", message);
            case "generate_study_plan" -> Map.of("weakness", message);
            case "analyze_project_experience" -> Map.of("projectText", message);
            case "generate_resume_suggestion" -> Map.of("resumeText", message);
            default -> Map.of("query", message);
        };
    }

    private String summarize(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    public record Execution(List<AgentPlanStep> steps, List<ToolResult> toolResults, int retryCount) { }
}
