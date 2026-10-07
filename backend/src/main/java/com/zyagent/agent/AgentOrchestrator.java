package com.zyagent.agent;

import com.zyagent.ai.AiChatService;
import com.zyagent.ai.TokenHandler;
import com.zyagent.config.ZyagentProperties;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.skill.IntentSignals;
import com.zyagent.skill.SkillDefinition;
import com.zyagent.skill.SkillRouter;
import com.zyagent.tool.AgentToolService;
import com.zyagent.tool.SimpleTool;
import com.zyagent.tool.ToolDefinition;
import com.zyagent.tool.ToolRegistry;
import com.zyagent.tool.ToolResult;
import com.zyagent.agent.task.AgentExecutionListener;
import com.zyagent.agent.task.AgentExecutionPolicy;
import com.zyagent.agent.task.AgentTask;
import com.zyagent.agent.task.AgentTaskLimits;
import com.zyagent.agent.task.AgentTaskService;
import com.zyagent.agent.task.AgentTaskState;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

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

@Service
public class AgentOrchestrator {
    private final AiChatService aiChatService;
    private final SkillRouter skillRouter;
    private final AgentToolService agentToolService;
    private final ChatMemoryService chatMemoryService;
    private final PromptAdvisorChain advisors;
    private final int replanMaxAttempts;
    private final MultiAgentCoordinator multiAgentCoordinator = new MultiAgentCoordinator();
    private final ToolRegistry toolRegistry = new ToolRegistry();
    private final AgentTaskService agentTaskService;
    private final AgentExecutionPolicy executionPolicy;
    private final ExecutorService toolExecutor;

    public AgentOrchestrator(
        AiChatService aiChatService,
        SkillRouter skillRouter,
        AgentToolService agentToolService,
        ChatMemoryService chatMemoryService,
        PromptAdvisorChain advisors,
        ZyagentProperties properties,
        AgentTaskService agentTaskService
    ) {
        this.aiChatService = aiChatService;
        this.skillRouter = skillRouter;
        this.agentToolService = agentToolService;
        this.chatMemoryService = chatMemoryService;
        this.advisors = advisors;
        this.replanMaxAttempts = properties.agent() == null ? 1 : Math.max(0, properties.agent().replanMaxAttempts());
        this.agentTaskService = agentTaskService;
        this.executionPolicy = new AgentExecutionPolicy(toLimits(properties.task()));
        this.toolExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "zyagent-tool-executor");
            thread.setDaemon(true);
            return thread;
        });
        registerToolDefinitions();
    }

    private static AgentTaskLimits toLimits(ZyagentProperties.Task task) {
        if (task == null) {
            return AgentTaskLimits.defaults();
        }
        return new AgentTaskLimits(
            task.maxPlanSteps(), task.maxToolCalls(), task.timeoutMs(), task.toolTimeoutMs(), task.maxRetries());
    }

    @PreDestroy
    void shutdownToolExecutor() {
        toolExecutor.shutdownNow();
    }

    /** {@code mysql} 表示任务已落库，{@code memory} 表示仅本地演示降级。 */
    public String persistenceMode() {
        return agentTaskService.persistenceMode();
    }

    public AgentResult execute(AgentMode mode, String message) {
        return execute(mode, message, null);
    }

    public AgentResult execute(AgentMode mode, String message, String sessionId) {
        AgentRun run = prepare(mode, message, sessionId);
        try {
            String answer = aiChatService.complete(run.skill().mode().prompt(), advisedPrompt(run, message, sessionId));
            agentTaskService.succeed(run.task(), answer);
            return new AgentResult(
                run.skill().mode(), run.skill(), run.plan(), run.toolResults(), run.references(), answer,
                run.routeDecision(), estimateUsage(run, message, sessionId, answer), run.metrics(),
                run.memorySnapshot(), run.collaborationTrace()
            );
        } catch (RuntimeException ex) {
            agentTaskService.fail(run.task(), "LLM_ERROR", ex.getMessage());
            throw ex;
        }
    }

    public AgentRun prepare(AgentMode mode, String message) {
        return prepare(mode, message, null);
    }

    public AgentRun prepare(AgentMode mode, String message, String sessionId) {
        return prepare(mode, message, sessionId, null, null);
    }

    public AgentRun prepare(AgentMode mode, String message, String sessionId, String idempotencyKey, AgentExecutionListener listener) {
        return prepareWith(null, mode, message, sessionId, idempotencyKey, listener);
    }

    private AgentRun prepareWith(
        AgentTask existingTask,
        AgentMode mode,
        String message,
        String sessionId,
        String idempotencyKey,
        AgentExecutionListener listener
    ) {
        ChatMemorySnapshot memorySnapshot = chatMemoryService.snapshot(sessionId);
        AgentRouteDecision routeDecision = skillRouter.routeDecision(mode, message, memorySnapshot);
        SkillDefinition skill = skillRouter.route(routeDecision);
        // 把本轮路由结果写回会话任务状态：它才是下一轮 Active Skill 的权威来源。
        chatMemoryService.saveTaskState(sessionId,
            routeDecision.category() == null ? null : routeDecision.category().name(),
            skill.id(), IntentSignals.studyDayNumber(message));
        AgentTask task = existingTask == null
            ? agentTaskService.create(sessionId, skill.mode(), message, idempotencyKey)
            : existingTask;
        if (task.state() != AgentTaskState.PENDING) {
            // 幂等键命中已存在的任务：不重复执行，直接复用当前状态与结果。
            return new AgentRun(task, skill, routeDecision, new AgentPlan(skill.mode(), List.of()), List.of(), null,
                AgentRunMetrics.from(List.of(), null, 0), memorySnapshot, CollaborationTrace.empty(), true);
        }
        AgentExecutionListener stepListener = listener == null ? persistingListener() : listener;
        AgentExecution execution = react(skill, message, task.id(), stepListener);
        DocumentSearchResponse references = referencesFrom(execution.toolResults());
        AgentRunMetrics metrics = AgentRunMetrics.from(execution.toolResults(), references, execution.retryCount());
        CollaborationTrace collaborationTrace = multiAgentCoordinator.coordinate(routeDecision, skill, message, memorySnapshot, execution.toolResults(), references);
        AgentTask started = agentTaskService.start(task);
        return new AgentRun(started, skill, routeDecision, new AgentPlan(skill.mode(), execution.steps()),
            execution.toolResults(), references, metrics, memorySnapshot, collaborationTrace, false);
    }

    /** 无外部监听时的默认监听：把每个步骤的开始与结束状态写入任务步骤表。 */
    private AgentExecutionListener persistingListener() {
        return new AgentExecutionListener() {
            @Override
            public void onStepStarted(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step);
            }

            @Override
            public void onStepFinished(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step);
            }
        };
    }

    /**
     * 显式重试一个已结束的任务：创建后继任务并整体重跑，不在 LLM 生成中途续跑。
     */
    public AgentTask retry(AgentTask original) {
        AgentTask retryTask = agentTaskService.create(original.sessionId(), original.mode(), original.requestText(), null);
        AgentRun run = prepareWith(retryTask, original.mode(), original.requestText(), original.sessionId(), null, null);
        try {
            String answer = aiChatService.complete(
                run.skill().mode().prompt(),
                advisedPrompt(run, original.requestText(), original.sessionId()));
            return agentTaskService.succeed(run.task(), answer);
        } catch (RuntimeException ex) {
            return agentTaskService.fail(run.task(), "RETRY_ERROR", ex.getMessage());
        }
    }

    public void streamAnswer(AgentRun run, String message, TokenHandler handler) {
        streamAnswer(run, message, null, handler);
    }

    public void streamAnswer(AgentRun run, String message, String sessionId, TokenHandler handler) {
        aiChatService.stream(run.skill().mode().prompt(), advisedPrompt(run, message, sessionId), handler);
    }

    public TokenUsage estimateUsage(AgentRun run, String message, String sessionId, String answer) {
        String prompt = run.skill().mode().prompt() + "\n" + advisedPrompt(run, message, sessionId);
        return TokenEstimator.estimate(prompt, answer);
    }

    public AgentTask complete(AgentRun run, String answer) {
        return agentTaskService.succeed(run.task(), answer);
    }

    public AgentTask fail(AgentRun run, String code, String message) {
        return agentTaskService.fail(run.task(), code, message);
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    private AgentExecution react(SkillDefinition skill, String message, String taskId, AgentExecutionListener listener) {
        List<ToolResult> results = new ArrayList<>();
        List<AgentPlanStep> steps = new ArrayList<>();
        int retryCount = 0;
        for (int index = 0; index < skill.planSteps().size(); index++) {
            int stepNo = index + 1;
            String toolName = index < skill.toolNames().size() ? skill.toolNames().get(index) : null;
            AgentPlanStep planned = AgentPlanStep.planned(skill.planSteps().get(index), toolName)
                .withInputSummary(summarize(message));
            if (!executionPolicy.withinPlanSteps(index)) {
                steps.add(finishStep(listener, taskId, stepNo,
                    planned.skipped("超过最大计划步骤上限 " + executionPolicy.limits().maxPlanSteps())));
                continue;
            }
            if (toolName == null || "match_resume_job".equals(toolName)) {
                steps.add(finishStep(listener, taskId, stepNo, planned.skipped("该步骤由最终回答综合完成")));
                continue;
            }
            if (!IntentSignals.hasContentForTool(toolName, message)) {
                // 元反馈或"执行既有计划"类消息不能当作工具的内容输入，否则会用无效参数生成垃圾结论。
                steps.add(finishStep(listener, taskId, stepNo, planned.skipped("本轮消息不是该工具的有效输入，已跳过")));
                continue;
            }
            if (!executionPolicy.withinToolCalls(results.size())) {
                steps.add(finishStep(listener, taskId, stepNo,
                    planned.skipped("超过最大工具调用上限 " + executionPolicy.limits().maxToolCalls())));
                continue;
            }
            listener.onStepStarted(taskId, stepNo, planned.running());
            long started = System.nanoTime();
            int attempt = 0;
            ToolResult result;
            do {
                attempt++;
                result = executeTool(toolName, message);
            } while (!result.success() && executionPolicy.canRetry(toolName, attempt));
            results.add(result);
            if (attempt > 1) {
                retryCount += attempt - 1;
            }
            long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            String outputSummary = result.success() ? summarize(String.valueOf(result.output())) : result.errorMessage();
            AgentPlanStep executed = planned.withAttempt(attempt);
            AgentPlanStep finished = result.success()
                ? executed.withOutputSummary(outputSummary).success(durationMs)
                : executed.withOutputSummary(outputSummary).failed(result.errorMessage(), durationMs);
            steps.add(finishStep(listener, taskId, stepNo, finished));
            if (!result.success() && replanMaxAttempts > 0) {
                retryCount++;
                AgentPlanStep replanned = AgentPlanStep.planned("根据工具失败结果调整回答策略", null).replanned(result.errorMessage());
                steps.add(replanned);
                listener.onStepFinished(taskId, steps.size(), replanned);
            }
        }
        return new AgentExecution(steps, results, retryCount);
    }

    private AgentPlanStep finishStep(AgentExecutionListener listener, String taskId, int stepNo, AgentPlanStep step) {
        listener.onStepFinished(taskId, stepNo, step);
        return step;
    }

    private ToolResult executeTool(String toolName, String message) {
        Map<String, Object> input = inputFor(toolName, message);
        Future<Object> future;
        try {
            future = toolExecutor.submit(() -> agentToolService.execute(toolName, input));
        } catch (RejectedExecutionException ex) {
            return ToolResult.failure(toolName, "工具执行器不可用");
        }
        try {
            Object output = future.get(executionPolicy.limits().toolTimeoutMs(), TimeUnit.MILLISECONDS);
            return ToolResult.success(toolName, output, output);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return ToolResult.failure(toolName, "工具执行超时（" + executionPolicy.limits().toolTimeoutMs() + "ms）");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.failure(toolName, "工具执行被中断");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            return ToolResult.failure(toolName, cause.getMessage());
        }
    }

    private Map<String, Object> inputFor(String toolName, String message) {
        return switch (toolName) {
            case "parse_job_description" -> Map.of("rawText", message);
            case "search_personal_knowledge" -> Map.of("query", message);
            case "generate_interview_questions" -> Map.of("jobText", message);
            case "generate_study_plan" -> Map.of("weakness", message);
            case "analyze_project_experience" -> Map.of("projectText", message);
            case "generate_resume_suggestion" -> Map.of("resumeText", message);
            default -> Map.of("query", message);
        };
    }

    /**
     * 组装提示词时按 Skill 作用域取上下文：切换任务后不再把上一个 Skill 的长输出带进新任务，
     * 避免上下文串线；澄清场景需要更宽的上下文来提出合适的澄清问题。
     */
    private String advisedPrompt(AgentRun run, String message, String sessionId) {
        boolean clarify = run.routeDecision() != null && run.routeDecision().needsClarification();
        String memory = clarify
            ? chatMemoryService.render(sessionId)
            : chatMemoryService.renderForSkill(sessionId, run.skill().id());
        String base = advisors.render(message, memory, run.toolResults());
        CollaborationTrace collaborationTrace = run.collaborationTrace();
        if (collaborationTrace == null || collaborationTrace.agents().isEmpty()) {
            return base;
        }
        String artifacts = collaborationTrace.artifacts().stream()
            .map(artifact -> "- " + artifact.producer() + "/" + artifact.type() + ": " + artifact.summary())
            .reduce("", (left, right) -> left + right + "\n");
        return base + "\n\n多 Agent 协作中间结果：\n"
            + artifacts
            + "Reviewer 复核结论：" + collaborationTrace.finalReview() + "\n"
            + "最终回答只输出面向用户的结论、步骤和建议。不要在正文中新增或重复‘证据来源’、‘置信度’、‘风险’、‘协作链路’等审计章节；这些信息已经由前端的 Agent 执行详情面板单独展示。回答仍需基于可用证据，证据不足时用自然语言说明限制。";
    }

    private void registerToolDefinitions() {
        for (ToolDefinition definition : agentToolService.definitions()) {
            toolRegistry.register(new SimpleTool(
                definition.name(),
                definition.description(),
                definition.inputSchema(),
                input -> agentToolService.execute(definition.name(), input)
            ));
        }
    }

    private DocumentSearchResponse referencesFrom(List<ToolResult> toolResults) {
        return toolResults.stream()
            .filter(ToolResult::success)
            .map(ToolResult::output)
            .filter(DocumentSearchResponse.class::isInstance)
            .map(DocumentSearchResponse.class::cast)
            .findFirst()
            .orElse(null);
    }

    private String summarize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private record AgentExecution(List<AgentPlanStep> steps, List<ToolResult> toolResults, int retryCount) {
    }

    public record AgentRun(
        AgentTask task,
        SkillDefinition skill,
        AgentRouteDecision routeDecision,
        AgentPlan plan,
        List<ToolResult> toolResults,
        DocumentSearchResponse references,
        AgentRunMetrics metrics,
        ChatMemorySnapshot memorySnapshot,
        CollaborationTrace collaborationTrace,
        boolean reused
    ) {
    }
}
