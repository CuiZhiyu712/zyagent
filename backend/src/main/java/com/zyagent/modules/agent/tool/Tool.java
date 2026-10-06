package com.zyagent.modules.agent.tool;

import java.util.Map;

public interface Tool {
    ToolDefinition definition();

    Object execute(Map<String, Object> input);
}
