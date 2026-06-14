package com.zyagent.tool;

import com.zyagent.agent.AgentOrchestrator;
import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final AgentOrchestrator orchestrator;

    public ToolController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping({"", "/definitions"})
    public ApiResponse<List<ToolDefinition>> definitions() {
        return ApiResponse.ok(orchestrator.toolRegistry().definitions());
    }

    @GetMapping("/calls")
    public ApiResponse<List<String>> calls() {
        return ApiResponse.ok(List.of("Tool call persistence is wired in the schema and ready for repository implementation."));
    }
}
