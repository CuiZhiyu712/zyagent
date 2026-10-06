package com.zyagent.modules.job;

import java.util.List;

public class JobDescriptionParserTest {
    public static void run() {
        parsesChineseInternetJobDescription();
    }

    private static void parsesChineseInternetJobDescription() {
        String rawText = """
            公司：字节跳动
            岗位：Java 后端开发实习生
            城市：北京
            岗位类型：实习
            技术方向：后端
            岗位职责：
            1. 参与推荐系统后端服务开发；
            2. 优化接口性能和系统稳定性。
            任职要求：
            1. 熟悉 Java、Spring Boot、MySQL、Redis；
            2. 了解 JVM 和计算机网络。
            加分项：
            有高并发项目经验。
            """;

        JobPosting posting = new JobDescriptionParser().parse(rawText, "https://jobs.example.com/1");

        TestAssertions.equals("字节跳动", posting.company(), "company");
        TestAssertions.equals("Java 后端开发实习生", posting.title(), "title");
        TestAssertions.equals("北京", posting.city(), "city");
        TestAssertions.equals("实习", posting.jobType(), "jobType");
        TestAssertions.equals("后端", posting.direction(), "direction");
        TestAssertions.containsAll(posting.skills(), List.of("Java", "Spring Boot", "MySQL", "Redis", "JVM"), "skills");
        TestAssertions.equals("https://jobs.example.com/1", posting.sourceUrl(), "sourceUrl");
    }
}
