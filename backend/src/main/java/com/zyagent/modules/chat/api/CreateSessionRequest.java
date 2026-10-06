package com.zyagent.modules.chat.api;

import com.zyagent.modules.agent.AgentMode;

public record CreateSessionRequest(String sessionId, String title, AgentMode agentMode) {
}
