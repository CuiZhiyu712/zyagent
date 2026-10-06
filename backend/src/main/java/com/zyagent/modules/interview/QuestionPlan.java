package com.zyagent.modules.interview;

/**
 * 一个待提问的问题。{@code usable=false} 表示模型输出无法解析、用了有标识的兜底问题。
 */
public record QuestionPlan(String question, boolean usable, String note) {
}
