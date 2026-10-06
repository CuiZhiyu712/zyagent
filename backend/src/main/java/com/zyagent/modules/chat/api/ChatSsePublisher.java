package com.zyagent.modules.chat.api;

import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.AgentPipelineTrace;
import com.zyagent.modules.agent.TokenUsage;
import com.zyagent.modules.agent.task.AgentTask;
import com.zyagent.modules.agent.task.AgentTaskStatusUpdate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/** Keeps the public SSE event names and payload mapping in one protocol adapter. */
@Component
public class ChatSsePublisher {
    public void initialEvents(SseEmitter emitter, AgentOrchestrator.AgentRun run, String persistenceMode) throws IOException {
        taskStatus(emitter, run.task(), persistenceMode);
        emitter.send(SseEmitter.event().name("skill").data(run.skill()));
        emitter.send(SseEmitter.event().name("route").data(run.routeDecision()));
        emitter.send(SseEmitter.event().name("plan").data(run.plan()));
        emitter.send(SseEmitter.event().name("tools").data(run.toolResults()));
        emitter.send(SseEmitter.event().name("metrics").data(run.metrics()));
        emitter.send(SseEmitter.event().name("memory").data(run.memorySnapshot()));
        emitter.send(SseEmitter.event().name("collaboration").data(run.collaborationTrace()));
        emitter.send(SseEmitter.event().name("pipeline").data(pipeline(run)));
        if (run.references() != null) emitter.send(SseEmitter.event().name("references").data(run.references()));
    }

    public void token(SseEmitter emitter, String token) throws IOException {
        emitter.send(SseEmitter.event().name("message").data(token));
    }

    public void usage(SseEmitter emitter, TokenUsage usage) throws IOException {
        emitter.send(SseEmitter.event().name("usage").data(usage));
    }

    public void taskStatus(SseEmitter emitter, AgentTask task, String persistenceMode) throws IOException {
        emitter.send(SseEmitter.event().name("task").data(task));
        emitter.send(SseEmitter.event().name("status").data(AgentTaskStatusUpdate.from(task, persistenceMode)));
    }

    public void stepQuietly(SseEmitter emitter, Object step) {
        try { emitter.send(SseEmitter.event().name("step").data(step)); } catch (IOException | RuntimeException ignored) { }
    }

    public AgentPipelineTrace pipeline(AgentOrchestrator.AgentRun run) {
        List<String> tools = run.toolResults().stream().map(result -> result.toolName()).toList();
        List<String> agents = run.collaborationTrace().agents().stream().map(trace -> trace.role().name()).toList();
        return AgentPipelineTrace.of(run.routeDecision().category().name(), tools, agents);
    }
}
