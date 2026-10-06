package com.zyagent.modules.job;

import java.time.LocalDateTime;
import java.util.List;

public final class MockJobSource {
    private MockJobSource() {
    }

    public static List<JobPosting> seed() {
        return List.of(
            job("mock-bytedance-java", "字节跳动", "Java 后端开发实习生", "北京", List.of("Java", "Spring Boot", "MySQL", "Redis", "JVM", "agent", "大模型")),
            job("mock-meituan-java", "美团", "后端开发工程师-校招", "上海", List.of("Java", "Spring Cloud", "MySQL", "Redis", "高并发")),
            job("mock-tencent-java", "腾讯", "后台开发实习生", "深圳", List.of("Java", "Linux", "计算机网络", "MySQL", "Redis")),
            job("mock-huawei-java", "华为", "软件开发工程师", "杭州", List.of("Java", "算法", "操作系统", "分布式", "微服务"))
        );
    }

    private static JobPosting job(String id, String company, String title, String city, List<String> skills) {
        String raw = "公司：" + company + "\n岗位：" + title + "\n城市：" + city + "\n岗位类型：校招\n技术方向：后端\n任职要求：熟悉 " + String.join("、", skills);
        LocalDateTime now = LocalDateTime.now();
        return new JobPosting(
            id,
            company,
            title,
            city,
            "校招",
            "后端",
            List.of("参与核心业务后端服务开发", "建设高可用、高性能系统"),
            List.of("熟悉 " + String.join("、", skills)),
            List.of("有项目实践和性能优化经验"),
            skills,
            "mock://" + id,
            "mock",
            id,
            null,
            null,
            now,
            now,
            "COLLECTED",
            raw
        );
    }
}
