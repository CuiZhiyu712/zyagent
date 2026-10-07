package com.zyagent.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.agent.AgentMode;
import com.zyagent.agent.AgentOrchestrator;
import com.zyagent.agent.AgentPipelineTrace;
import com.zyagent.agent.AgentPlanStep;
import com.zyagent.agent.AgentResult;
import com.zyagent.agent.TokenUsage;
import com.zyagent.agent.task.AgentExecutionListener;
import com.zyagent.agent.task.AgentTask;
import com.zyagent.agent.task.AgentTaskService;
import com.zyagent.agent.task.AgentTaskStatusUpdate;
import com.zyagent.agent.task.AgentTaskStep;
import com.zyagent.common.ApiResponse;
import com.zyagent.storage.ChatMessageView;
import com.zyagent.storage.ChatRepository;
import com.zyagent.storage.ChatSessionView;
import com.zyagent.storage.ToolCallRecordRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AgentOrchestrator orchestrator;
    private final AgentTaskService agentTaskService;
    private final ChatRepository chatRepository;
    private final ToolCallRecordRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    public ChatController(
        AgentOrchestrator orchestrator,
        AgentTaskService agentTaskService,
        ObjectProvider<ChatRepository> chatRepository,
        ObjectProvider<ToolCallRecordRepository> toolCallRepository,
        ObjectMapper objectMapper
    ) {
        this.orchestrator = orchestrator;
        this.agentTaskService = agentTaskService;
        this.chatRepository = chatRepository.getIfAvailable();
        this.toolCallRepository = toolCallRepository.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionView>> sessions() {
        if (chatRepository == null) {
            return ApiResponse.ok(List.of());
        }
        try {
            return ApiResponse.ok(chatRepository.listSessions());
        } catch (RuntimeException ex) {
            return ApiResponse.ok(List.of());
        }
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionView> createSession(@RequestBody CreateSessionRequest request) {
        String title = request == null ? "新对话" : request.title();
        AgentMode mode = request == null ? null : request.agentMode();
        String id = request == null ? null : request.sessionId();
        if (chatRepository == null) {
            return ApiResponse.ok(new ChatSessionView(id == null ? UUID.randomUUID().toString() : id, title, mode == null ? "AUTO" : mode.name(), 0, null, null));
        }
        try {
            return ApiResponse.ok(chatRepository.createSession(id, title, mode));
        } catch (RuntimeException ex) {
            return ApiResponse.ok(new ChatSessionView(id == null ? UUID.randomUUID().toString() : id, title, mode == null ? "AUTO" : mode.name(), 0, null, null));
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        if (chatRepository == null) {
            return ApiResponse.fail("数据库不可用");
        }
        try {
            chatRepository.deleteSession(sessionId);
            return ApiResponse.ok(null);
        } catch (RuntimeException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageView>> messages(@PathVariable String sessionId) {
        if (chatRepository == null) {
            return ApiResponse.ok(List.of());
        }
        try {
            return ApiResponse.ok(chatRepository.listMessages(sessionId));
        } catch (RuntimeException ex) {
            return ApiResponse.ok(List.of());
        }
    }

    @PostMapping("/complete")
    public ApiResponse<AgentResult> complete(@RequestBody ChatRequest request) {
        String sessionId = ensureSession(request);
        saveUserMessage(sessionId, request.message());
        AgentResult result = orchestrator.execute(request.agentMode(), request.message(), sessionId);
        saveTools(sessionId, result.skill().id(), result.toolResults());
        saveAssistantMessage(sessionId, result.answer(), result);
        return ApiResponse.ok(result);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Thread worker = new Thread(() -> {
            String sessionId = ensureSession(request);
            StringBuilder answer = new StringBuilder();
            AgentOrchestrator.AgentRun run = null;
            try {
                saveUserMessage(sessionId, request.message());
                run = orchestrator.prepare(request.agentMode(), request.message(), sessionId,
                    request.idempotencyKey(), streamingStepListener(emitter));
                if (run.reused()) {
                    // 幂等键命中已有任务：不重复执行，直接把当前状态与结果告知客户端。
                    emitTerminal(emitter, run.task());
                    emitter.complete();
                    return;
                }
                saveTools(sessionId, run.skill().id(), run.toolResults());
                emitRunning(emitter, run);
                emitter.send(SseEmitter.event().name("skill").data(run.skill()));
                emitter.send(SseEmitter.event().name("route").data(run.routeDecision()));
                emitter.send(SseEmitter.event().name("plan").data(run.plan()));
                emitter.send(SseEmitter.event().name("tools").data(run.toolResults()));
                emitter.send(SseEmitter.event().name("metrics").data(run.metrics()));
                emitter.send(SseEmitter.event().name("memory").data(run.memorySnapshot()));
                emitter.send(SseEmitter.event().name("collaboration").data(run.collaborationTrace()));
                emitter.send(SseEmitter.event().name("pipeline").data(pipeline(run)));
                if (run.references() != null) {
                    emitter.send(SseEmitter.event().name("references").data(run.references()));
                }
                orchestrator.streamAnswer(run, request.message(), sessionId, token -> {
                    try {
                        answer.append(token);
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                TokenUsage usage = orchestrator.estimateUsage(run, request.message(), sessionId, answer.toString());
                AgentTask task = orchestrator.complete(run, answer.toString());
                emitTerminal(emitter, task);
                emitter.send(SseEmitter.event().name("usage").data(usage));
                saveAssistantMessage(sessionId, answer.toString(), run, usage, task);
                emitter.complete();
            } catch (RuntimeException | IOException ex) {
                if (run != null) {
                    try {
                        emitTerminal(emitter, orchestrator.fail(run, "STREAM_ERROR", ex.getMessage()));
                    } catch (RuntimeException | IOException persistenceFailure) {
                        // Emitter may already be closed or persistence may be strict; the stored task state stays authoritative.
                    }
                }
                if (!answer.isEmpty()) {
                    saveAssistantMessage(sessionId, answer.toString(), "partial", null, null, null, List.of(), null, null, null, null, null, null);
                }
                emitter.completeWithError(ex);
            }
        });
        worker.setName("zyagent-sse-worker");
        worker.start();
        return emitter;
    }

    private void emitRunning(SseEmitter emitter, AgentOrchestrator.AgentRun run) throws IOException {
        emitter.send(SseEmitter.event().name("task").data(run.task()));
        emitter.send(SseEmitter.event().name("status").data(AgentTaskStatusUpdate.from(run.task(), orchestrator.persistenceMode())));
    }

    private AgentPipelineTrace pipeline(AgentOrchestrator.AgentRun run) {
        List<String> tools = run.toolResults().stream().map(result -> result.toolName()).toList();
        List<String> agents = run.collaborationTrace().agents().stream()
            .map(trace -> trace.role().name())
            .toList();
        return AgentPipelineTrace.of(run.routeDecision().category().name(), tools, agents);
    }

    /**
     * 步骤监听：每个步骤开始/结束时持久化并把 {@code step} 事件推给前端。
     *
     * <p>客户端断流时只静默跳过推送，不中断执行，保证任务仍能到达确定终态。
     */
    private AgentExecutionListener streamingStepListener(SseEmitter emitter) {
        return new AgentExecutionListener() {
            @Override
            public void onStepStarted(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step);
                sendQuietly(emitter, AgentTaskStep.from(taskId, stepNo, step));
            }

            @Override
            public void onStepFinished(String taskId, int stepNo, AgentPlanStep step) {
                agentTaskService.saveStep(taskId, stepNo, step);
                sendQuietly(emitter, AgentTaskStep.from(taskId, stepNo, step));
            }
        };
    }

    private void sendQuietly(SseEmitter emitter, Object step) {
        try {
            emitter.send(SseEmitter.event().name("step").data(step));
        } catch (IOException | RuntimeException ignored) {
            // 断流或推送失败不影响执行；已持久化的任务状态仍然权威。
        }
    }

    private void emitTerminal(SseEmitter emitter, AgentTask task) throws IOException {
        emitter.send(SseEmitter.event().name("task").data(task));
        emitter.send(SseEmitter.event().name("status").data(AgentTaskStatusUpdate.from(task, orchestrator.persistenceMode())));
    }

    private String ensureSession(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
            ? UUID.randomUUID().toString()
            : request.sessionId();
        if (chatRepository != null) {
            try {
                chatRepository.ensureSession(sessionId, titleFrom(request.message()), request.agentMode());
                chatRepository.touchSession(sessionId, titleFrom(request.message()), request.agentMode());
            } catch (RuntimeException ignored) {
                return sessionId;
            }
        }
        return sessionId;
    }

    private void saveUserMessage(String sessionId, String message) {
        if (chatRepository != null) {
            try {
                chatRepository.saveMessage(sessionId, "user", message, null);
            } catch (RuntimeException ignored) {
                // Chat remains available when MySQL credentials are not configured yet.
            }
        }
    }

    private void saveAssistantMessage(String sessionId, String message, AgentResult result) {
        saveAssistantMessage(sessionId, message, result.skill().id(), result.references(), result.routeDecision(), result.plan(), result.toolResults(), result.runMetrics(), result.tokenUsage(), result.memorySnapshot(), result.collaborationTrace(), null, null);
    }

    private void saveAssistantMessage(String sessionId, String message, String skillId, Object references) {
        saveAssistantMessage(sessionId, message, skillId, references, null, null, List.of(), null, null, null, null, null, null);
    }

    private void saveAssistantMessage(String sessionId, String message, AgentOrchestrator.AgentRun run, TokenUsage usage, AgentTask task) {
        saveAssistantMessage(sessionId, message, run.skill().id(), run.references(), run.routeDecision(), run.plan(), run.toolResults(), run.metrics(), usage, run.memorySnapshot(), run.collaborationTrace(), task, pipeline(run));
    }

    private void saveAssistantMessage(
        String sessionId,
        String message,
        String skillId,
        Object references,
        Object route,
        Object plan,
        Object tools,
        Object metrics,
        Object usage,
        Object memory,
        Object collaboration,
        Object task,
        Object pipeline
    ) {
        if (chatRepository != null) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("skill", skillId);
                payload.put("route", route);
                payload.put("plan", plan);
                payload.put("tools", tools);
                payload.put("metrics", metrics);
                payload.put("usage", usage);
                payload.put("memory", memory);
                payload.put("collaboration", collaboration);
                payload.put("pipeline", pipeline);
                if (task != null) {
                    payload.put("task", task);
                }
                if (references != null) {
                    payload.put("references", references);
                }
                chatRepository.saveMessage(sessionId, "assistant", message, objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException ex) {
                chatRepository.saveMessage(sessionId, "assistant", message, "{\"skill\":\"" + skillId + "\"}");
            } catch (RuntimeException ignored) {
                // Tool traces are still returned to the frontend even if persistence is unavailable.
            }
        }
    }

    private void saveTools(String sessionId, String skillId, List<com.zyagent.tool.ToolResult> results) {
        if (toolCallRepository != null) {
            try {
                toolCallRepository.saveAll(sessionId, skillId, results);
            } catch (RuntimeException ignored) {
                // Tool traces are still returned to the frontend even if persistence is unavailable.
            }
        }
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "新对话";
        }
        return message.length() > 18 ? message.substring(0, 18) : message;
    }

    public record ChatRequest(String sessionId, String message, AgentMode agentMode, String idempotencyKey) {
    }

    public record CreateSessionRequest(String sessionId, String title, AgentMode agentMode) {
    }
}
