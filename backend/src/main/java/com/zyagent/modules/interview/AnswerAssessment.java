package com.zyagent.modules.interview;

/**
 * 单轮回答的评估结果：结构化评价 + 是否追问。
 *
 * <p>{@code usable=false} 时 {@link #evaluation()} 是 fallback，分数不可信。
 */
public record AnswerAssessment(
    InterviewEvaluation evaluation,
    boolean followUp,
    String followUpQuestion,
    boolean usable,
    String note
) {
}
