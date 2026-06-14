package com.zyagent.agent;

import com.zyagent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptAdvisorChain {
    public String render(String userMessage, String memoryContext, List<ToolResult> toolResults) {
        StringBuilder prompt = new StringBuilder();
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append(memoryContext).append("\n\n");
        }
        prompt.append("当前用户问题：\n").append(userMessage == null ? "" : userMessage);
        if (toolResults != null && !toolResults.isEmpty()) {
            prompt.append("\n\n工具执行结果：\n");
            for (ToolResult result : toolResults) {
                prompt.append("- ")
                    .append(result.toolName())
                    .append(result.success() ? " 成功：" : " 失败：")
                    .append(result.success() ? result.output() : result.errorMessage())
                    .append('\n');
            }
        }
        return prompt.toString().strip();
    }
}
