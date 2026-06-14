package com.zyagent.tool;

import java.util.Map;
import java.util.function.Function;

public class SimpleTool implements Tool {
    private final ToolDefinition definition;
    private final Function<Map<String, Object>, Object> handler;

    public SimpleTool(String name, String description, Map<String, String> inputSchema, Function<Map<String, Object>, Object> handler) {
        this.definition = new ToolDefinition(name, description, inputSchema);
        this.handler = handler;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return handler.apply(input);
    }
}
