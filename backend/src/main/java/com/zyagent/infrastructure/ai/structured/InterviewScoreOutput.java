package com.zyagent.infrastructure.ai.structured;

import java.util.List;

public record InterviewScoreOutput(
    int score,
    List<String> strengths,
    List<String> improvements
) {
}
