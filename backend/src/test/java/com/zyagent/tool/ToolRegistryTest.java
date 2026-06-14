package com.zyagent.tool;

import com.zyagent.job.TestAssertions;

import java.util.Map;

public class ToolRegistryTest {
    public static void run() {
        registersAndExecutesNamedTool();
    }

    private static void registersAndExecutesNamedTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SimpleTool("echo", "Echo input", Map.of("text", "string"), input -> input.get("text")));

        ToolResult result = registry.execute("echo", Map.of("text", "hello"));

        TestAssertions.equals(true, result.success(), "success");
        TestAssertions.equals("hello", result.output(), "output");
        TestAssertions.isTrue(registry.definitions().stream().anyMatch(tool -> tool.name().equals("echo")), "definition exists");
    }
}
