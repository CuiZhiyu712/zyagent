package com.zyagent.tool;

import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.job.JobDescriptionParser;
import com.zyagent.job.JobPosting;
import com.zyagent.job.TestAssertions;

import java.util.List;

public class AgentToolServiceTest {
    public static void run() {
        exposesSpringAiToolDefinitions();
        parsesJobDescriptionThroughToolService();
    }

    private static void exposesSpringAiToolDefinitions() {
        AgentToolService service = new AgentToolService(
            (query, types) -> new DocumentSearchResponse("memory_fallback", List.of()),
            new JobDescriptionParser()
        );

        List<ToolDefinition> definitions = service.definitions();

        TestAssertions.isTrue(definitions.stream().anyMatch(tool -> tool.name().equals("search_personal_knowledge")), "knowledge tool");
        TestAssertions.isTrue(definitions.stream().anyMatch(tool -> tool.name().equals("parse_job_description")), "jd tool");
    }

    private static void parsesJobDescriptionThroughToolService() {
        AgentToolService service = new AgentToolService(
            (query, types) -> new DocumentSearchResponse("memory_fallback", List.of()),
            new JobDescriptionParser()
        );

        JobPosting posting = service.parseJobDescription("公司：字节\n岗位：Java 后端工程师\n城市：北京\n要求：Java、Spring Boot、Redis");

        TestAssertions.equals("字节", posting.company(), "company");
        TestAssertions.isTrue(posting.skills().contains("Java"), "skill");
    }
}
