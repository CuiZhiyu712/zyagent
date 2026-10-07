package com.zyagent.match;

import java.util.List;

/**
 * 岗位匹配报告。
 *
 * <p>{@code profileEvidence} 记录匹配到的技能来自哪条**已确认画像证据**（来源类型与引用），
 * 便于区分"简历里写过"与"画像已确认"。
 */
public record JobMatchReport(
    int score,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> strengths,
    List<String> suggestions,
    List<String> profileEvidence
) {
    public JobMatchReport(int score, List<String> matchedSkills, List<String> missingSkills,
                          List<String> strengths, List<String> suggestions) {
        this(score, matchedSkills, missingSkills, strengths, suggestions, List.of());
    }
}
