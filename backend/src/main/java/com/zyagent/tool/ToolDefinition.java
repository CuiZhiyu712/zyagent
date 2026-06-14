package com.zyagent.tool;

import java.util.Map;

public record ToolDefinition(
    String name,
    String description,
    Map<String, String> inputSchema
) {
}
