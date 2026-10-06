package com.zyagent.modules.resume.match;

import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.job.TestAssertions;

import java.time.LocalDateTime;
import java.util.List;

public class ResumeJobMatcherTest {
    public static void run() {
        scoresMatchedSkillsAndReportsGaps();
    }

    private static void scoresMatchedSkillsAndReportsGaps() {
        JobPosting posting = new JobPosting(
            "job-1",
            "美团",
            "Java 后端开发工程师",
            "上海",
            "校招",
            "后端",
            List.of("参与交易系统后端开发"),
            List.of("熟悉 Java、Spring Boot、MySQL、Redis、JVM"),
            List.of("有高并发经验"),
            List.of("Java", "Spring Boot", "MySQL", "Redis", "JVM"),
            "https://jobs.example.com/2",
            null,
            LocalDateTime.now(),
            "COLLECTED",
            "raw jd"
        );
        ResumeProfile resume = new ResumeProfile(
            "resume-1",
            "熟悉 Java、Spring Boot、MySQL，做过 Redis 缓存项目和订单系统。",
            List.of("Java", "Spring Boot", "MySQL", "Redis"),
            List.of("订单系统", "缓存优化")
        );

        JobMatchReport report = new ResumeJobMatcher().match(resume, posting);

        TestAssertions.isTrue(report.score() >= 80, "score");
        TestAssertions.containsAll(report.matchedSkills(), List.of("Java", "Spring Boot", "MySQL", "Redis"), "matchedSkills");
        TestAssertions.containsAll(report.missingSkills(), List.of("JVM"), "missingSkills");
        TestAssertions.isTrue(report.suggestions().stream().anyMatch(s -> s.contains("JVM")), "suggestions mention JVM");
    }
}
