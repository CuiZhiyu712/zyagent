package com.zyagent.interview;

import java.util.List;

/**
 * 单轮结构化评价：四个维度分数（0-5）、解释与证据引用。
 *
 * <p>{@code usable=false} 表示模型输出无法解析、使用了有标识的 fallback —— 此时分数不可信，
 * 不得当作有效评价计入成绩。
 */
public record InterviewEvaluation(
    int technicalCorrectness,
    int completeness,
    int projectEvidence,
    int expressionStructure,
    List<String> explanations,
    List<String> evidenceRefs,
    boolean usable,
    String note
) {
    private static final int MAX_SCORE = 5;

    public InterviewEvaluation {
        technicalCorrectness = clamp(technicalCorrectness);
        completeness = clamp(completeness);
        projectEvidence = clamp(projectEvidence);
        expressionStructure = clamp(expressionStructure);
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        note = note == null ? "" : note;
    }

    public static InterviewEvaluation fallback(String note) {
        return new InterviewEvaluation(0, 0, 0, 0, List.of(), List.of(), false, note);
    }

    public double average() {
        return (technicalCorrectness + completeness + projectEvidence + expressionStructure) / 4.0;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(MAX_SCORE, score));
    }
}
