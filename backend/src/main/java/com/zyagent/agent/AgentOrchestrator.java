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
        String answer = aiChatService.complete(run.skill().mode().prompt(), advisedPrompt(message, sessionId, run.toolResults()));
        return new AgentResult(run.skill().mode(), run.skill(), run.plan(), run.toolResults(), run.references(), answer);
    }

    public AgentRun prepare(AgentMode mode, String message) {
        return prepare(mode, message, null);
    }

    public AgentRun prepare(AgentMode mode, String message, String sessionId) {
        SkillDefinition skill = skillRouter.route(mode, message);
        AgentExecution execution = react(skill, message);
        return new AgentRun(skill, new AgentPlan(skill.mode(), execution.steps()), execution.toolResults(), referencesFrom(execution.toolResults()));
    }

    public void streamAnswer(AgentRun run, String message, TokenHandler handler) {
        streamAnswer(run, message, null, handler);
    }

    public void streamAnswer(AgentRun run, String message, String sessionId, TokenHandler handler) {
        aiChatService.stream(run.skill().mode().prompt(), advisedPrompt(message, sessionId, run.toolResults()), handler);
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    private AgentExecution react(SkillDefinition skill, String message) {
        List<ToolResult> results = new ArrayList<>();
        List<AgentPlanStep> steps = new ArrayList<>();
        for (int index = 0; index < skill.planSteps().size(); index++) {
            String toolName = index < skill.toolNames().size() ? skill.toolNames().get(index) : null;
            AgentPlanStep planned = AgentPlanStep.planned(skill.planSteps().get(index), toolName);
            if (toolName == null || "match_resume_job".equals(toolName)) {
                steps.add(planned.success(0L));
                continue;
            }
            long started = System.nanoTime();
            steps.add(planned.running());
            ToolResult result = executeTool(toolName, message);
            results.add(result);
            long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            steps.set(steps.size() - 1, result.success()
                ? planned.success(durationMs)
                : planned.failed(result.errorMessage(), durationMs));
            if (!result.success() && replanMaxAttempts > 0) {
                steps.add(AgentPlanStep.planned("根据工具失败结果调整回答策略", null).replanned(result.errorMessage()));
            }
        }
        return new AgentExecution(steps, results);
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

    private record AgentExecution(List<AgentPlanStep> steps, List<ToolResult> toolResults) {
    }

    public record AgentRun(SkillDefinition skill, AgentPlan plan, List<ToolResult> toolResults, DocumentSearchResponse references) {
    }
}
