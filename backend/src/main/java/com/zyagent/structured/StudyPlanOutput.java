package com.zyagent.structured;

import java.util.List;

public record StudyPlanOutput(
    String title,
    List<String> days,
    String summary
) {
}
