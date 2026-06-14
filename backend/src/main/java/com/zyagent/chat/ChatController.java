package com.zyagent.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.agent.AgentMode;
import com.zyagent.agent.AgentOrchestrator;
import com.zyagent.agent.AgentResult;
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
    private final ChatRepository chatRepository;
    private final ToolCallRecordRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    public ChatController(
        AgentOrchestrator orchestrator,
        ObjectProvider<ChatRepository> chatRepository,
        ObjectProvider<ToolCallRecordRepository> toolCallRepository,
        ObjectMapper objectMapper
    ) {
        this.orchestrator = orchestrator;
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
        saveAssistantMessage(sessionId, result.answer(), result.skill().id(), result.references());
        return ApiResponse.ok(result);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Thread worker = new Thread(() -> {
            String sessionId = ensureSession(request);
            StringBuilder answer = new StringBuilder();
            try {
                saveUserMessage(sessionId, request.message());
                AgentOrchestrator.AgentRun run = orchestrator.prepare(request.agentMode(), request.message(), sessionId);
                saveTools(sessionId, run.skill().id(), run.toolResults());
                emitter.send(SseEmitter.event().name("skill").data(run.skill()));
                emitter.send(SseEmitter.event().name("plan").data(run.plan()));
                emitter.send(SseEmitter.event().name("tools").data(run.toolResults()));
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
                saveAssistantMessage(sessionId, answer.toString(), run.skill().id(), run.references());
                emitter.complete();
            } catch (RuntimeException | IOException ex) {
                if (!answer.isEmpty()) {
                    saveAssistantMessage(sessionId, answer.toString(), "partial", null);
                }
                emitter.completeWithError(ex);
            }
        });
        worker.setName("zyagent-sse-worker");
        worker.start();
        return emitter;
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

    private void saveAssistantMessage(String sessionId, String message, String skillId, Object references) {
        if (chatRepository != null) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("skill", skillId);
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

    public record ChatRequest(String sessionId, String message, AgentMode agentMode) {
    }

    public record CreateSessionRequest(String sessionId, String title, AgentMode agentMode) {
    }
}
