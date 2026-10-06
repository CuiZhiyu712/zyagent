package com.zyagent.modules.chat;

import com.zyagent.common.ApiResponse;
import com.zyagent.infrastructure.storage.ChatMessageView;
import com.zyagent.infrastructure.storage.ChatSessionView;
import com.zyagent.modules.agent.AgentResult;
import com.zyagent.modules.chat.api.ChatRequest;
import com.zyagent.modules.chat.api.CreateSessionRequest;
import com.zyagent.modules.chat.application.ChatApplicationService;
import com.zyagent.modules.chat.application.ChatSessionService;
import com.zyagent.modules.chat.application.ChatStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatSessionService sessions;
    private final ChatApplicationService chatApplication;
    private final ChatStreamService chatStream;

    public ChatController(ChatSessionService sessions, ChatApplicationService chatApplication, ChatStreamService chatStream) {
        this.sessions = sessions;
        this.chatApplication = chatApplication;
        this.chatStream = chatStream;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionView>> sessions() { return ApiResponse.ok(sessions.sessions()); }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionView> createSession(@RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(sessions.create(request == null ? null : request.sessionId(), request == null ? null : request.title(), request == null ? null : request.agentMode()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        String error = sessions.deleteFailure(sessionId);
        return error == null ? ApiResponse.ok(null) : ApiResponse.fail(error);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageView>> messages(@PathVariable String sessionId) { return ApiResponse.ok(sessions.messages(sessionId)); }

    @PostMapping("/complete")
    public ApiResponse<AgentResult> complete(@RequestBody ChatRequest request) {
        return ApiResponse.ok(chatApplication.complete(request.sessionId(), request.message(), request.agentMode()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatStream.stream(request, emitter);
        return emitter;
    }
}
