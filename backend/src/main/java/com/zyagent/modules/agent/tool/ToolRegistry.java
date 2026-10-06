package com.zyagent.modules.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.definition().name(), tool);
    }

    public ToolResult execute(String name, Map<String, Object> input) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure(name, "Tool not found: " + name);
        }
        try {
            return ToolResult.success(name, tool.execute(input));
        } catch (RuntimeException ex) {
            return ToolResult.failure(name, ex.getMessage());
        }
    }

    public List<ToolDefinition> definitions() {
        return new ArrayList<>(tools.values().stream().map(Tool::definition).toList());
    }
}
