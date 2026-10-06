package com.zyagent.modules.agent;

import com.zyagent.infrastructure.ai.AiChatService;
import com.zyagent.infrastructure.ai.TokenHandler;
import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.tool.AgentToolService;
import com.zyagent.modules.agent.tool.SimpleTool;
import com.zyagent.modules.agent.tool.ToolDefinition;
import com.zyagent.modules.agent.tool.ToolRegistry;
import com.zyagent.modules.agent.tool.ToolResult;
import com.zyagent.modules.agent.runtime.ConversationPipeline;
import com.zyagent.modules.agent.runtime.ConversationContextAssembler;
import com.zyagent.modules.agent.skill.SkillPromptCatalog;
import com.zyagent.modules.agent.task.AgentExecutionListener;
import com.zyagent.modules.agent.task.AgentTask;
import com.zyagent.modules.agent.task.AgentTaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentOrchestrator {
    private final AiChatService aiChatService;
    private final AgentToolService agentToolService;
    private final ToolRegistry toolRegistry;
    private final AgentTaskService agentTaskService;
    private final ConversationPipeline conversationPipeline;
    private final ConversationContextAssembler contextAssembler;
    private final SkillPromptCatalog skillPrompts;

    public AgentOrchestrator(
        AiChatService aiChatService,
        AgentToolService agentToolService,
        AgentTaskService agentTaskService,
        ConversationPipeline conversationPipeline,
        ConversationContextAssembler contextAssembler,
        ToolRegistry toolRegistry,
        SkillPromptCatalog skillPrompts
    ) {
        this.aiChatService = aiChatService;
        this.agentToolService = agentToolService;
        this.agentTaskService = agentTaskService;
        this.conversationPipeline = conversationPipeline;
        this.contextAssembler = contextAssembler;
        this.toolRegistry = toolRegistry;
        this.skillPrompts = skillPrompts;
        registerToolDefinitions();
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
            String answer = aiChatService.complete(skillPrompts.systemPrompt(run.skill().mode()), advisedPrompt(run, message, sessionId));
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

    private AgentRun prepareWith(AgentTask existingTask, AgentMode mode, String message, String sessionId,
                                 String idempotencyKey, AgentExecutionListener listener) {
        return conversationPipeline.prepare(existingTask, mode, message, sessionId, idempotencyKey, listener);
    }

    /**
     * 显式重试一个已结束的任务：创建后继任务并整体重跑，不在 LLM 生成中途续跑。
     */
    public AgentTask retry(AgentTask original) {
        AgentTask retryTask = agentTaskService.create(original.sessionId(), original.mode(), original.requestText(), null);
        AgentRun run = prepareWith(retryTask, original.mode(), original.requestText(), original.sessionId(), null, null);
        try {
            String answer = aiChatService.complete(
                skillPrompts.systemPrompt(run.skill().mode()),
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
        aiChatService.stream(skillPrompts.systemPrompt(run.skill().mode()), advisedPrompt(run, message, sessionId), handler);
    }

    public TokenUsage estimateUsage(AgentRun run, String message, String sessionId, String answer) {
        String prompt = skillPrompts.systemPrompt(run.skill().mode()) + "\n" + advisedPrompt(run, message, sessionId);
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

    private String advisedPrompt(AgentRun run, String message, String sessionId) {
        return contextAssembler.assemble(run, message, sessionId);
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
