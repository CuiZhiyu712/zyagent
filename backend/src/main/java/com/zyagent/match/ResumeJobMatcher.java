package com.zyagent.match;

import com.zyagent.job.JobPosting;
import com.zyagent.profile.UserSkill;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ResumeJobMatcher {
    public JobMatchReport match(ResumeProfile resume, JobPosting posting) {
        return match(resume, posting, List.of());
    }

    /**
     * 匹配简历与岗位；{@code confirmedSkills} 是已确认画像技能，会并入技能集合并标注出处。
     */
    public JobMatchReport match(ResumeProfile resume, JobPosting posting, List<UserSkill> confirmedSkills) {
        Set<String> resumeSkills = new LinkedHashSet<>(resume.skills());
        for (UserSkill skill : confirmedSkills) {
            resumeSkills.add(skill.skillKey());
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String rawText = resume.rawText() == null ? "" : resume.rawText().toLowerCase(Locale.ROOT);
        for (String skill : posting.skills()) {
            if (containsIgnoreCase(resumeSkills, skill) || rawText.contains(skill.toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        List<String> strengths = new ArrayList<>();
        List<String> profileEvidence = new ArrayList<>();
        for (String skill : matched) {
            UserSkill confirmed = findConfirmed(confirmedSkills, skill);
            if (confirmed == null) {
                strengths.add("简历中已体现 " + skill + " 能力");
                continue;
            }
            strengths.add("画像已确认 " + skill + "（" + confirmed.level() + "）");
            profileEvidence.add(skill + " ← " + confirmed.sourceType()
                + (confirmed.sourceId() == null || confirmed.sourceId().isBlank() ? "" : "#" + confirmed.sourceId())
                + "，置信度 " + String.format(Locale.ROOT, "%.2f", confirmed.confidence()));
        }

        int required = Math.max(posting.skills().size(), 1);
        int score = Math.min(100, Math.round((matched.size() * 100.0f) / required));
        List<String> suggestions = missing.isEmpty()
            ? List.of("岗位核心技能覆盖较完整，建议补充可量化的项目结果。")
            : missing.stream().map(skill -> "补充 " + skill + " 的学习记录、项目实践或面试表达。").toList();

        return new JobMatchReport(score, matched, missing, strengths, suggestions, profileEvidence);
    }

    private UserSkill findConfirmed(List<UserSkill> confirmedSkills, String skill) {
        return confirmedSkills.stream()
            .filter(candidate -> candidate.skillKey().equalsIgnoreCase(skill))
            .findFirst()
            .orElse(null);
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }
}
