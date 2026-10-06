package com.zyagent.modules.agent.runtime;

import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.ChatMemoryService;
import com.zyagent.modules.agent.prompt.SkillPromptComposer;
import org.springframework.stereotype.Component;

@Component
public class ConversationContextAssembler {
    private final ChatMemoryService memory;
    private final SkillPromptComposer prompts;

    public ConversationContextAssembler(ChatMemoryService memory, SkillPromptComposer prompts) {
        this.memory = memory;
        this.prompts = prompts;
    }

    public String assemble(AgentOrchestrator.AgentRun run, String message, String sessionId) {
        boolean clarify = run.routeDecision() != null && run.routeDecision().needsClarification();
        String context = clarify ? memory.render(sessionId) : memory.renderForSkill(sessionId, run.skill().id());
        // Collaboration traces remain visible as execution metadata; they are not dumped into the answer prompt.
        return prompts.compose(message, context, run.toolResults());
    }
}
