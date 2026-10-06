package com.zyagent.modules.chat.api;

import com.zyagent.modules.agent.AgentMode;

public record ChatRequest(String sessionId, String message, AgentMode agentMode, String idempotencyKey) {
}
