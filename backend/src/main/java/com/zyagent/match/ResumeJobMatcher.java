package com.zyagent.match;

import com.zyagent.job.JobPosting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ResumeJobMatcher {
    public JobMatchReport match(ResumeProfile resume, JobPosting posting) {
        Set<String> resumeSkills = new LinkedHashSet<>(resume.skills());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : posting.skills()) {
            if (containsIgnoreCase(resumeSkills, skill) || resume.rawText().toLowerCase().contains(skill.toLowerCase())) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int required = Math.max(posting.skills().size(), 1);
        int score = Math.min(100, Math.round((matched.size() * 100.0f) / required));
        List<String> strengths = matched.stream().map(skill -> "简历中已体现 " + skill + " 能力").toList();
        List<String> suggestions = missing.isEmpty()
            ? List.of("岗位核心技能覆盖较完整，建议补充可量化的项目结果。")
            : missing.stream().map(skill -> "补充 " + skill + " 的学习记录、项目实践或面试表达。").toList();

        return new JobMatchReport(score, matched, missing, strengths, suggestions);
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }
}
