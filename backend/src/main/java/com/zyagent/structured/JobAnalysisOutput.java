package com.zyagent.structured;

import java.util.List;

public record JobAnalysisOutput(
    String title,
    String company,
    List<String> skills,
    List<String> suggestions
) {
}
