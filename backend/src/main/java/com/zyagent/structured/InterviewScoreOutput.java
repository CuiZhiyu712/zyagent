package com.zyagent.structured;

import java.util.List;

public record InterviewScoreOutput(
    int score,
    List<String> strengths,
    List<String> improvements
) {
}
