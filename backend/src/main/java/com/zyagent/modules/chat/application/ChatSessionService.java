package com.zyagent.modules.chat.application;

import com.zyagent.infrastructure.storage.ChatMessageView;
import com.zyagent.infrastructure.storage.ChatRepository;
import com.zyagent.infrastructure.storage.ChatSessionView;
import com.zyagent.infrastructure.storage.ToolCallRecordRepository;
import com.zyagent.modules.agent.AgentResult;
import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.TokenUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.modules.agent.AgentMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatSessionService {
    private final ChatRepository chatRepository;
    private final ToolCallRecordRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    public ChatSessionService(ObjectProvider<ChatRepository> chatRepository,
                              ObjectProvider<ToolCallRecordRepository> toolCallRepository,
                              ObjectMapper objectMapper) {
        this.chatRepository = chatRepository.getIfAvailable();
        this.toolCallRepository = toolCallRepository.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public List<ChatSessionView> sessions() {
        if (chatRepository == null) return List.of();
        try { return chatRepository.listSessions(); } catch (RuntimeException ex) { return List.of(); }
    }

    public ChatSessionView create(String id, String title, AgentMode mode) {
        String resolvedId = id == null ? UUID.randomUUID().toString() : id;
        String resolvedMode = mode == null ? "AUTO" : mode.name();
        if (chatRepository != null) {
            try { return chatRepository.createSession(id, title, mode); } catch (RuntimeException ignored) { }
        }
        return new ChatSessionView(resolvedId, title, resolvedMode, 0, null, null);
    }

    public String deleteFailure(String sessionId) {
        if (chatRepository == null) return "数据库不可用";
        try { chatRepository.deleteSession(sessionId); return null; }
        catch (RuntimeException ex) { return ex.getMessage(); }
    }

    public List<ChatMessageView> messages(String sessionId) {
        if (chatRepository == null) return List.of();
        try { return chatRepository.listMessages(sessionId); } catch (RuntimeException ex) { return List.of(); }
    }

    public String ensureSession(String requestedId, String message, AgentMode mode) {
        String sessionId = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString() : requestedId;
        if (chatRepository != null) {
            try {
                String title = titleFrom(message);
                chatRepository.ensureSession(sessionId, title, mode);
                chatRepository.touchSession(sessionId, title, mode);
            } catch (RuntimeException ignored) { }
        }
        return sessionId;
    }

    public void saveUserMessage(String sessionId, String message) {
        if (chatRepository != null) try { chatRepository.saveMessage(sessionId, "user", message, null); } catch (RuntimeException ignored) { }
    }

    public void saveTools(String sessionId, String skillId, List<com.zyagent.modules.agent.tool.ToolResult> results) {
        if (toolCallRepository != null) try { toolCallRepository.saveAll(sessionId, skillId, results); } catch (RuntimeException ignored) { }
    }

    public void saveAssistant(String sessionId, AgentResult result) {
        saveAssistantPayload(sessionId, result.answer(), result.skill().id(), result.references(), result.routeDecision(), result.plan(), result.toolResults(), result.runMetrics(), result.tokenUsage(), result.memorySnapshot(), result.collaborationTrace(), null, null);
    }

    public void saveAssistant(String sessionId, String answer, AgentOrchestrator.AgentRun run, TokenUsage usage, Object task, Object pipeline) {
        saveAssistantPayload(sessionId, answer, run.skill().id(), run.references(), run.routeDecision(), run.plan(), run.toolResults(), run.metrics(), usage, run.memorySnapshot(), run.collaborationTrace(), task, pipeline);
    }

    public void savePartialAssistant(String sessionId, String answer) {
        saveAssistantPayload(sessionId, answer, "partial", null, null, null, List.of(), null, null, null, null, null, null);
    }

    private void saveAssistantPayload(String sessionId, String message, String skillId, Object references, Object route, Object plan,
                                      Object tools, Object metrics, Object usage, Object memory, Object collaboration, Object task, Object pipeline) {
        if (chatRepository == null) return;
        try {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("skill", skillId); payload.put("route", route); payload.put("plan", plan); payload.put("tools", tools);
            payload.put("metrics", metrics); payload.put("usage", usage); payload.put("memory", memory);
            payload.put("collaboration", collaboration); payload.put("pipeline", pipeline);
            if (task != null) payload.put("task", task);
            if (references != null) payload.put("references", references);
            chatRepository.saveMessage(sessionId, "assistant", message, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            chatRepository.saveMessage(sessionId, "assistant", message, "{\"skill\":\"" + skillId + "\"}");
        } catch (RuntimeException ignored) { }
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) return "新对话";
        return message.length() > 18 ? message.substring(0, 18) : message;
    }
}
