package com.zyagent.tool;

import com.zyagent.document.DocumentService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {
    @Bean
    KnowledgeSearchPort knowledgeSearchPort(DocumentService documentService) {
        return documentService::searchWithReferences;
    }

    @Bean
    ToolCallback[] zyagentToolCallbacks(AgentToolService tools) {
        return ToolCallbacks.from(tools);
    }
}
