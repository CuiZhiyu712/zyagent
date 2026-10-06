package com.zyagent.modules.agent.tool;

import com.zyagent.modules.agent.port.KnowledgeSearchPort;
import com.zyagent.modules.agent.port.JobAnalysisPort;
import com.zyagent.modules.agent.port.ResumeAdvicePort;
import com.zyagent.modules.agent.port.InterviewPracticePort;
import com.zyagent.modules.knowledgebase.DocumentService;
import com.zyagent.modules.job.JobDescriptionParser;
import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.resume.match.ResumeJobMatcher;
import com.zyagent.modules.resume.match.ResumeProfile;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {
    @Bean
    KnowledgeSearchPort knowledgeSearchPort(DocumentService documentService) {
        return documentService::searchWithReferences;
    }

    @Bean
    JobAnalysisPort jobAnalysisPort(JobDescriptionParser parser) {
        ResumeJobMatcher matcher = new ResumeJobMatcher();
        return new JobAnalysisPort() {
            @Override public JobPosting parse(String rawText) { return parser.parse(rawText, null); }
            @Override public com.zyagent.modules.resume.match.JobMatchReport match(String resumeText, String jobText) {
                JobPosting posting = parser.parse(jobText, null);
                return matcher.match(new ResumeProfile("tool-resume", resumeText, java.util.List.of(), java.util.List.of()), posting);
            }
        };
    }

    @Bean
    ResumeAdvicePort resumeAdvicePort() {
        return new ResumeAdvicePort() {
            @Override public java.util.List<String> analyzeProject(String projectText) {
                return java.util.List.of("业务背景是否清晰", "技术选型需要说明取舍", "准备性能、稳定性和排障追问");
            }
            @Override public java.util.List<String> suggest(String resumeText) {
                return java.util.List.of("用结果量化项目贡献", "把技术栈和业务效果放在同一条经历里", "为每个项目准备 STAR 表达");
            }
        };
    }

    @Bean
    InterviewPracticePort interviewPracticePort() {
        return jobText -> java.util.List.of("请介绍一个你最熟悉的后端项目。", "Redis 缓存一致性如何处理？", "MySQL 索引失效有哪些常见场景？");
    }

    @Bean
    ToolCallback[] zyagentToolCallbacks(AgentToolService tools) {
        return ToolCallbacks.from(tools);
    }
}
