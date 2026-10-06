package com.zyagent.infrastructure.ai.structured;

import java.util.List;

public record StudyPlanOutput(
    String title,
    List<String> days,
    String summary
) {
}
