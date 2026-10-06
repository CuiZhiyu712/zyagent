package com.zyagent.infrastructure.ai.structured;

import com.zyagent.modules.job.TestAssertions;

import java.util.List;

public class StructuredOutputTest {
    public static void run() {
        parsesStudyPlanJson();
        fallsBackForInvalidJson();
    }

    private static void parsesStudyPlanJson() {
        StructuredOutputService service = new StructuredOutputService();

        StudyPlanOutput output = service.parseStudyPlan("""
            {"title":"Redis 复习计划","days":["梳理基础","项目复盘"],"summary":"两天完成"}
            """);

        TestAssertions.equals("Redis 复习计划", output.title(), "title");
        TestAssertions.equals(List.of("梳理基础", "项目复盘"), output.days(), "days");
    }

    private static void fallsBackForInvalidJson() {
        StructuredOutputService service = new StructuredOutputService();

        StudyPlanOutput output = service.parseStudyPlan("不是 JSON");

        TestAssertions.equals("学习计划", output.title(), "fallback title");
        TestAssertions.isTrue(output.days().get(0).contains("不是 JSON"), "fallback content");
    }
}
