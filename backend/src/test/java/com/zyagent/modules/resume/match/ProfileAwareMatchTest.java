package com.zyagent.modules.resume.match;

import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.profile.SkillLevel;
import com.zyagent.modules.profile.UserSkill;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileAwareMatchTest {
    @Test
    void confirmedProfileSkillIsMatchedWithEvidenceSource() {
        JobPosting posting = posting(List.of("Java", "Redis"));
        ResumeProfile resume = new ResumeProfile("r-1", "熟悉 Java", List.of("Java"), List.of());
        UserSkill confirmed = UserSkill.of("local-user", "Redis", SkillLevel.WORKING, 0.6,
            "面试第 1 轮", "INTERVIEW", "session-1#turn-1");

        JobMatchReport report = new ResumeJobMatcher().match(resume, posting, List.of(confirmed));

        assertTrue(report.matchedSkills().contains("Redis"), "confirmed profile skill counts as matched");
        assertEquals(1, report.profileEvidence().size(), "evidence recorded for the confirmed skill");
        assertTrue(report.profileEvidence().get(0).contains("INTERVIEW"), "evidence cites source type");
        assertTrue(report.profileEvidence().get(0).contains("session-1#turn-1"), "evidence cites source id");
        assertTrue(report.strengths().stream().anyMatch(line -> line.contains("画像已确认")), "strength mentions confirmed profile");
    }

    @Test
    void withoutProfileSkillsBehaviourIsResumeOnly() {
        JobPosting posting = posting(List.of("Java", "Redis"));
        ResumeProfile resume = new ResumeProfile("r-1", "熟悉 Java", List.of("Java"), List.of());

        JobMatchReport report = new ResumeJobMatcher().match(resume, posting, List.of());

        assertTrue(report.profileEvidence().isEmpty());
        assertTrue(report.missingSkills().contains("Redis"), "unconfirmed skill still reported as a gap");
    }

    private static JobPosting posting(List<String> skills) {
        return new JobPosting("job-1", "美团", "Java 后端", "上海", "校招", "后端",
            List.of(), List.of(), List.of(), skills, "https://jobs.example.com/1", null,
            LocalDateTime.now(), "COLLECTED", "raw jd");
    }
}
