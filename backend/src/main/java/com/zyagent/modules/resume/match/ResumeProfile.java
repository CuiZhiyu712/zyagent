package com.zyagent.modules.resume.match;

import java.util.List;

public record ResumeProfile(
    String id,
    String rawText,
    List<String> skills,
    List<String> projects
) {
}
