package com.zyagent.skill;

import java.util.List;

public record ContextIntentResult(
    String intent,
    String skillId,
    String stage,
    double confidence,
    List<String> contextRefs
) {
    public static ContextIntentResult empty() {
        return new ContextIntentResult("", "", "", 0D, List.of());
    }

    public boolean usable() {
        return skillId != null && !skillId.isBlank() && confidence >= 0.72D;
    }
}
