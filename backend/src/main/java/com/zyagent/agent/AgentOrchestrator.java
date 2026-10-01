package com.zyagent.agent;

import com.zyagent.ai.AiChatService;
import com.zyagent.ai.TokenHandler;
import com.zyagent.config.ZyagentProperties;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.skill.SkillDefinition;
import com.zyagent.skill.SkillRouter;
import com.zyagent.tool.AgentToolService;
import com.zyagent.tool.SimpleTool;
import com.zyagent.tool.ToolDefinition;
import com.zyagent.tool.ToolRegistry;
import com.zyagent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public AgentOrchestrator(
        AiChatService aiChatService,
        SkillRouter skillRouter,
        AgentToolService agentToolService,
        ChatMemoryService chatMemoryService,
        PromptAdvisorChain advisors,
        ZyagentProperties properties
    ) {
        this.aiChatService = aiChatService;
        this.skillRouter = skillRouter;
        this.agentToolService = agentToolService;
        this.chatMemoryService = chatMemoryService;
        this.advisors = advisors;
        this.replanMaxAttempts = properties.agent() == null ? 1 : Math.max(0, properties.agent().replanMaxAttempts());
        registerToolDefinitions();
    }

    public AgentResult execute(AgentMode mode, String message) {
        return execute(mode, message, null);
    }

    public AgentResult execute(AgentMode mode, String message, String sessionId) {
        AgentRun run = prepare(mode, message, sessionId);
        String answer = aiChatService.complete(run.skill().mode().prompt(), advisedPrompt(message, sessionId, run.toolResults(), run.collaborationTrace()));
        return new AgentResult(
            run.skill().mode(),
            run.skill(),
            run.plan(),
            run.toolResults(),
            run.references(),
            answer,
            run.routeDecision(),
            estimateUsage(run, message, sessionId, answer),
            run.metrics(),
            run.memorySnapshot(),
            run.collaborationTrace()
        );
    }

    public AgentRun prepare(AgentMode mode, String message) {
        return prepare(mode, message, null);
    }

    public AgentRun prepare(AgentMode mode, String message, String sessionId) {
        AgentRouteDecision routeDecision = skillRouter.routeDecision(mode, message);
        SkillDefinition skill = skillRouter.route(mode, message);
        ChatMemorySnapshot memorySnapshot = chatMemoryService.snapshot(sessionId);
        AgentExecution execution = react(skill, message);
        DocumentSearchResponse references = referencesFrom(execution.toolResults());
        AgentRunMetrics metrics = AgentRunMetrics.from(execution.toolResults(), references, execution.retryCount());
        CollaborationTrace collaborationTrace = multiAgentCoordinator.coordinate(routeDecision, skill, message, memorySnapshot, execution.toolResults(), references);
        return new AgentRun(skill, routeDecision, new AgentPlan(skill.mode(), execution.steps()), execution.toolResults(), references, metrics, memorySnapshot, collaborationTrace);
    }

    public void streamAnswer(AgentRun run, String message, TokenHandler handler) {
        streamAnswer(run, message, null, handler);
    }

    public void streamAnswer(AgentRun run, String message, String sessionId, TokenHandler handler) {
        aiChatService.stream(run.skill().mode().prompt(), advisedPrompt(message, sessionId, run.toolResults(), run.collaborationTrace()), handler);
    }

    public TokenUsage estimateUsage(AgentRun run, String message, String sessionId, String answer) {
        String prompt = run.skill().mode().prompt() + "\n" + advisedPrompt(message, sessionId, run.toolResults(), run.collaborationTrace());
        return TokenEstimator.estimate(prompt, answer);
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    private AgentExecution react(SkillDefinition skill, String message) {
        List<ToolResult> results = new ArrayList<>();
        List<AgentPlanStep> steps = new ArrayList<>();
        int retryCount = 0;
        for (int index = 0; index < skill.planSteps().size(); index++) {
            String toolName = index < skill.toolNames().size() ? skill.toolNames().get(index) : null;
            AgentPlanStep planned = AgentPlanStep.planned(skill.planSteps().get(index), toolName)
                .withInputSummary(summarize(message));
            if (toolName == null || "match_resume_job".equals(toolName)) {
                steps.add(planned.skipped("该步骤由最终回答综合完成"));
                continue;
            }
            long started = System.nanoTime();
            steps.add(planned.running());
            ToolResult result = executeTool(toolName, message);
            results.add(result);
            long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            String outputSummary = result.success() ? summarize(String.valueOf(result.output())) : result.errorMessage();
            steps.set(steps.size() - 1, result.success()
                ? planned.withOutputSummary(outputSummary).success(durationMs)
                : planned.withOutputSummary(outputSummary).failed(result.errorMessage(), durationMs));
            if (!result.success() && replanMaxAttempts > 0) {
                retryCount++;
                steps.add(AgentPlanStep.planned("根据工具失败结果调整回答策略", null).replanned(result.errorMessage()));
            }
        }
        return new AgentExecution(steps, results, retryCount);
    }

    private ToolResult executeTool(String toolName, String message) {
        try {
            Object output = agentToolService.execute(toolName, inputFor(toolName, message));
            return ToolResult.success(toolName, output, output);
        } catch (RuntimeException ex) {
            return ToolResult.failure(toolName, ex.getMessage());
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

    private String advisedPrompt(String message, String sessionId, List<ToolResult> toolResults) {
        return advisors.render(message, chatMemoryService.render(sessionId), toolResults);
    }

    private String advisedPrompt(String message, String sessionId, List<ToolResult> toolResults, CollaborationTrace collaborationTrace) {
        String base = advisedPrompt(message, sessionId, toolResults);
        if (collaborationTrace == null || collaborationTrace.agents().isEmpty()) {
            return base;
        }
        String artifacts = collaborationTrace.artifacts().stream()
            .map(artifact -> "- " + artifact.producer() + "/" + artifact.type() + ": " + artifact.summary())
            .reduce("", (left, right) -> left + right + "\n");
        return base + "\n\n多 Agent 协作中间结果：\n"
            + artifacts
            + "Reviewer 复核结论：" + collaborationTrace.finalReview() + "\n"
            + "最终回答必须引用 Planner、Retriever、Evaluator、Reviewer 的关键中间结果，说明证据来源、置信度和风险。";
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
        SkillDefinition skill,
        AgentRouteDecision routeDecision,
        AgentPlan plan,
        List<ToolResult> toolResults,
        DocumentSearchResponse references,
        AgentRunMetrics metrics,
        ChatMemorySnapshot memorySnapshot,
        CollaborationTrace collaborationTrace
    ) {
    }
}
