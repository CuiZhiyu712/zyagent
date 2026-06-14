package com.zyagent.tool;

public record ToolResult(
    boolean success,
    String toolName,
    Object output,
    String errorMessage,
    Object structuredOutput
) {
    public ToolResult(boolean success, String toolName, Object output, String errorMessage) {
        this(success, toolName, output, errorMessage, null);
    }

    public static ToolResult success(String toolName, Object output) {
        return new ToolResult(true, toolName, output, null, null);
    }

    public static ToolResult success(String toolName, Object output, Object structuredOutput) {
        return new ToolResult(true, toolName, output, null, structuredOutput);
    }

    public static ToolResult failure(String toolName, String errorMessage) {
        return new ToolResult(false, toolName, null, errorMessage, null);
    }
}
