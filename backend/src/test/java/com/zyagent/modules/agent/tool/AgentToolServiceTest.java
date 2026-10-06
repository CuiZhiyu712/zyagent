package com.zyagent.modules.agent.tool;

import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.job.JobDescriptionParser;
import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.job.TestAssertions;
import com.zyagent.modules.agent.port.JobAnalysisPort;
import com.zyagent.modules.agent.port.KnowledgeSearchPort;
import com.zyagent.modules.agent.port.ResumeAdvicePort;
import com.zyagent.modules.resume.match.ResumeJobMatcher;
import com.zyagent.modules.resume.match.ResumeProfile;

import java.util.List;

public class AgentToolServiceTest {
    public static void run() {
        exposesSpringAiToolDefinitions();
        parsesJobDescriptionThroughToolService();
    }

    private static void exposesSpringAiToolDefinitions() {
        AgentToolService service = service((query, types) -> new DocumentSearchResponse("memory_fallback", List.of()));

        List<ToolDefinition> definitions = service.definitions();

        TestAssertions.isTrue(definitions.stream().anyMatch(tool -> tool.name().equals("search_personal_knowledge")), "knowledge tool");
        TestAssertions.isTrue(definitions.stream().anyMatch(tool -> tool.name().equals("parse_job_description")), "jd tool");
    }

    private static void parsesJobDescriptionThroughToolService() {
        AgentToolService service = service((query, types) -> new DocumentSearchResponse("memory_fallback", List.of()));

        JobPosting posting = service.parseJobDescription("公司：字节\n岗位：Java 后端工程师\n城市：北京\n要求：Java、Spring Boot、Redis");

        TestAssertions.equals("字节", posting.company(), "company");
        TestAssertions.isTrue(posting.skills().contains("Java"), "skill");
    }

    private static AgentToolService service(KnowledgeSearchPort search) {
        ResumeAdvicePort advice = new ResumeAdvicePort() {
            @Override public List<String> analyzeProject(String projectText) { return List.of(); }
            @Override public List<String> suggest(String resumeText) { return List.of(); }
        };
        return new AgentToolService(search, jobPort(), advice, job -> List.of());
    }

    private static JobAnalysisPort jobPort() {
        JobDescriptionParser parser = new JobDescriptionParser();
        ResumeJobMatcher matcher = new ResumeJobMatcher();
        return new JobAnalysisPort() {
            @Override public JobPosting parse(String rawText) { return parser.parse(rawText, null); }
            @Override public com.zyagent.modules.resume.match.JobMatchReport match(String resumeText, String jobText) {
                return matcher.match(new ResumeProfile("test", resumeText, List.of(), List.of()), parser.parse(jobText, null));
            }
        };
    }
}
