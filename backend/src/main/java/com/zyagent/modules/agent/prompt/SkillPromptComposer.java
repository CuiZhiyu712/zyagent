package com.zyagent.modules.agent.prompt;

import com.zyagent.modules.agent.PromptAdvisorChain;
import com.zyagent.modules.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Composes the user-facing prompt from the selected skill's context and this turn's evidence. */
@Component
public class SkillPromptComposer {
    private final PromptAdvisorChain advisors;

    public SkillPromptComposer(PromptAdvisorChain advisors) { this.advisors = advisors; }

    public String compose(String message, String context, List<ToolResult> evidence) {
        return advisors.render(message, context, evidence);
    }
}
