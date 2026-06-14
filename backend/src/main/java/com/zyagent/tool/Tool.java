package com.zyagent.tool;

import java.util.Map;

public interface Tool {
    ToolDefinition definition();

    Object execute(Map<String, Object> input);
}
