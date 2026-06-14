package com.zyagent.match;

import java.util.List;

public record JobMatchReport(
    int score,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> strengths,
    List<String> suggestions
) {
}
