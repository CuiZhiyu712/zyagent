package com.zyagent.modules.chat.application;

import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.AgentResult;
import org.springframework.stereotype.Service;

@Service
public class ChatApplicationService {
    private final AgentOrchestrator orchestrator;
    private final ChatSessionService sessions;

    public ChatApplicationService(AgentOrchestrator orchestrator, ChatSessionService sessions) {
        this.orchestrator = orchestrator;
        this.sessions = sessions;
    }

    public AgentResult complete(String requestedSessionId, String message, com.zyagent.modules.agent.AgentMode mode) {
        String sessionId = sessions.ensureSession(requestedSessionId, message, mode);
        sessions.saveUserMessage(sessionId, message);
        AgentResult result = orchestrator.execute(mode, message, sessionId);
        sessions.saveTools(sessionId, result.skill().id(), result.toolResults());
        sessions.saveAssistant(sessionId, result);
        return result;
    }
}
