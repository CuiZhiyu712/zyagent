package com.zyagent.modules.chat.application;

import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.AgentPlanStep;
import com.zyagent.modules.agent.TokenUsage;
import com.zyagent.modules.agent.task.AgentExecutionListener;
import com.zyagent.modules.agent.task.AgentTask;
import com.zyagent.modules.agent.task.AgentTaskService;
import com.zyagent.modules.agent.task.AgentTaskStep;
import com.zyagent.modules.chat.api.ChatRequest;
import com.zyagent.modules.chat.api.ChatSsePublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class ChatStreamService {
    private final AgentOrchestrator orchestrator;
    private final AgentTaskService agentTaskService;
    private final ChatSessionService sessions;
    private final ChatSsePublisher publisher;

    public ChatStreamService(AgentOrchestrator orchestrator, AgentTaskService agentTaskService, ChatSessionService sessions, ChatSsePublisher publisher) {
        this.orchestrator = orchestrator;
        this.agentTaskService = agentTaskService;
        this.sessions = sessions;
        this.publisher = publisher;
    }

    public void stream(ChatRequest request, SseEmitter emitter) {
        Thread worker = new Thread(() -> run(request, emitter), "zyagent-sse-worker");
        worker.start();
    }

    private void run(ChatRequest request, SseEmitter emitter) {
        String sessionId = sessions.ensureSession(request.sessionId(), request.message(), request.agentMode());
        StringBuilder answer = new StringBuilder();
        AgentOrchestrator.AgentRun run = null;
        try {
            sessions.saveUserMessage(sessionId, request.message());
            run = orchestrator.prepare(request.agentMode(), request.message(), sessionId, request.idempotencyKey(), streamingStepListener(emitter));
            if (run.reused()) {
                publisher.taskStatus(emitter, run.task(), orchestrator.persistenceMode());
                emitter.complete();
                return;
            }
            sessions.saveTools(sessionId, run.skill().id(), run.toolResults());
            publisher.initialEvents(emitter, run, orchestrator.persistenceMode());
            orchestrator.streamAnswer(run, request.message(), sessionId, token -> {
                try { answer.append(token); publisher.token(emitter, token); }
                catch (IOException ex) { throw new IllegalStateException(ex); }
            });
            TokenUsage usage = orchestrator.estimateUsage(run, request.message(), sessionId, answer.toString());
            AgentTask task = orchestrator.complete(run, answer.toString());
            publisher.taskStatus(emitter, task, orchestrator.persistenceMode());
            publisher.usage(emitter, usage);
            sessions.saveAssistant(sessionId, answer.toString(), run, usage, task, publisher.pipeline(run));
            emitter.complete();
        } catch (RuntimeException | IOException ex) {
            if (run != null) {
                try { publisher.taskStatus(emitter, orchestrator.fail(run, "STREAM_ERROR", ex.getMessage()), orchestrator.persistenceMode()); }
                catch (RuntimeException | IOException ignored) { /* Persisted task state remains authoritative. */ }
            }
            if (!answer.isEmpty()) sessions.savePartialAssistant(sessionId, answer.toString());
            emitter.completeWithError(ex);
        }
    }

    private AgentExecutionListener streamingStepListener(SseEmitter emitter) {
        return new AgentExecutionListener() {
            @Override public void onStepStarted(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step); publisher.stepQuietly(emitter, AgentTaskStep.from(taskId, stepNo, step));
            }
            @Override public void onStepFinished(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step); publisher.stepQuietly(emitter, AgentTaskStep.from(taskId, stepNo, step));
            }
        };
    }

}
