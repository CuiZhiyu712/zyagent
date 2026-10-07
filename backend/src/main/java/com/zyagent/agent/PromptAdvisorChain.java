package com.zyagent.agent;

import com.zyagent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 组装最终提示词。
 *
 * <p>除了拼接上下文与工具结果，还负责注入**证据与一致性约束**：
 * 工具结果只是素材；无命中时必须说明证据不足，不得编造题量、模块数或题号范围；
 * 上文已有计划结构时必须沿用其编号与边界。
 */
@Service
public class PromptAdvisorChain {
    private static final Map<Integer, String> CJK_DAY = Map.ofEntries(
        Map.entry(1, "一"), Map.entry(2, "二"), Map.entry(3, "三"), Map.entry(4, "四"),
        Map.entry(5, "五"), Map.entry(6, "六"), Map.entry(7, "七"), Map.entry(8, "八"),
        Map.entry(9, "九"), Map.entry(10, "十"), Map.entry(11, "十一"), Map.entry(12, "十二"),
        Map.entry(13, "十三"), Map.entry(14, "十四"));
    /**
     * 匹配 Day1 / D1 / day 3 这类标记。
     *
     * <p>刻意不用尾随 {@code \b}：Java 的 {@code \b} 把中文汉字也视为 word 字符，
     * "Day1的学习" 会被判定为无边界，导致按 Day 切片静默失效。这里改用 lookaround。
     */
    private static final Pattern ASCII_DAY = Pattern.compile("(?i)(?:day|(?<![A-Za-z])D)\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern CJK_DAY_TOKEN = Pattern.compile("第\\s*(十[一二三四]|[一二三四五六七八九十]|1[0-4]|[1-9])\\s*天");

    /** 任意 Day 标记，用于定位"计划头"与第一天展开内容的分界。 */
    private static final Pattern ANY_DAY = Pattern.compile(
        "(?i)(?:day|(?<![A-Za-z])D)\\s*\\d{1,2}(?!\\d)|第\\s*(?:十[一二三四]|[一二三四五六七八九十]|1[0-4]|[1-9])\\s*天");
    private static final List<String> CJK_ORDER = List.of(
        "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四");

    public String render(String userMessage, String memoryContext, List<ToolResult> toolResults) {
        StringBuilder prompt = new StringBuilder();
        String plannedDay = plannedDayContext(userMessage, memoryContext);
        if (plannedDay.isBlank()) {
            if (memoryContext != null && !memoryContext.isBlank()) {
                prompt.append(memoryContext).append("\n\n");
            }
        } else {
            // 只保留计划头与当前 Day，避免模型顺着上下文滑到别的 Day 上。
            prompt.append("上一轮学习计划的上下文（必须优先遵循，不要重新发明题目）：\n")
                .append(plannedDay)
                .append("\n\n执行要求：只讲该 Day 的题目和答案；如果计划已经给出题号或模块，沿用原题号和模块，不要改成泛化的 Agent/RAG 基础课。\n\n");
        }
        prompt.append("当前用户问题：\n").append(userMessage == null ? "" : userMessage);
        if (toolResults != null && !toolResults.isEmpty()) {
            prompt.append("\n\n工具执行结果：\n");
            for (ToolResult result : toolResults) {
                prompt.append("- ")
                    .append(result.toolName())
                    .append(result.success() ? "：" : " 失败：")
                    .append(describe(result))
                    .append('\n');
            }
        }
        prompt.append("\n\n回答约束：\n")
            .append("1. 工具结果只是素材，不是已确认的结论；不要把它里面的模板文字或占位内容原样当作计划、标题或答案输出。\n")
            .append("2. 工具无命中（标注为“无命中”）、失败或被跳过时，明确说明“未检索到相关证据”，不得编造题量、模块数、题号范围、文档内容或数字。\n")
            .append("3. 引用知识库结论时给出文档名与 chunk 序号；没有引用就不要给出确定性结论。\n")
            .append("4. “最近对话上下文”里如果已经有计划或结构，必须沿用它的编号与边界，不要重新发明一套。\n")
            .append("5. 用户在对上一轮回答做元层面反馈或纠错时，先复述你理解的差异点，再给修正，不要开启新的业务产出。");
        return prompt.toString().strip();
    }

    private String describe(ToolResult result) {
        if (!result.success()) {
            return result.errorMessage() == null ? "" : result.errorMessage();
        }
        Object output = result.output();
        boolean empty = output instanceof List<?> list
            ? list.isEmpty()
            : output == null || String.valueOf(output).isBlank();
        return empty ? "（无命中）" : String.valueOf(output);
    }

    /** 从用户消息里识别要执行的学习计划 Day；支持 Day3 / D3 / 第3天 / 第三天。 */
    private String plannedDayContext(String userMessage, String memoryContext) {
        if (userMessage == null || memoryContext == null || memoryContext.isBlank()) {
            return "";
        }
        Integer day = extractDay(userMessage);
        if (day == null) {
            return "";
        }
        int start = indexOfDay(memoryContext, day);
        if (start < 0) {
            return "";
        }
        int next = indexOfDay(memoryContext, day + 1);
        String section = (next > start ? memoryContext.substring(start, next) : memoryContext.substring(start)).strip();
        if (section.length() > 7000) {
            section = section.substring(0, 7000) + "...";
        }
        // 计划头（第一个 Day 标记之前的内容）保留，其余 Day 的展开内容丢弃。
        Matcher firstMarker = ANY_DAY.matcher(memoryContext);
        String header = firstMarker.find() ? memoryContext.substring(0, firstMarker.start()).strip() : "";
        return header.isEmpty() ? section : header + "\n\n" + section;
    }

    private Integer extractDay(String userMessage) {
        Matcher ascii = ASCII_DAY.matcher(userMessage);
        if (ascii.find()) {
            return Integer.parseInt(ascii.group(1));
        }
        Matcher cjk = CJK_DAY_TOKEN.matcher(userMessage);
        if (!cjk.find()) {
            return null;
        }
        String token = cjk.group(1);
        if (token.matches("\\d+")) {
            return Integer.parseInt(token);
        }
        int index = CJK_ORDER.indexOf(token) + 1;
        return index <= 0 ? null : index;
    }

    private int indexOfDay(String text, int day) {
        Matcher ascii = Pattern.compile("(?i)(?:day|(?<![A-Za-z])D)\\s*" + day + "(?!\\d)").matcher(text);
        if (ascii.find()) {
            return ascii.start();
        }
        String cjk = CJK_DAY.get(day);
        if (cjk == null) {
            return -1;
        }
        Matcher cjkMatcher = Pattern.compile("第\\s*" + cjk + "\\s*天").matcher(text);
        return cjkMatcher.find() ? cjkMatcher.start() : -1;
    }
}
