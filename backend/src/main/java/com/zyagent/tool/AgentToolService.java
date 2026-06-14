package com.zyagent.tool;

import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.job.JobDescriptionParser;
import com.zyagent.job.JobPosting;
import com.zyagent.match.JobMatchReport;
import com.zyagent.match.ResumeJobMatcher;
import com.zyagent.match.ResumeProfile;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentToolService {
    private final KnowledgeSearchPort knowledgeSearch;
    private final JobDescriptionParser parser;
    private final ResumeJobMatcher matcher = new ResumeJobMatcher();

    public AgentToolService(KnowledgeSearchPort knowledgeSearch, JobDescriptionParser parser) {
        this.knowledgeSearch = knowledgeSearch;
        this.parser = parser;
    }

    @Tool(name = "search_personal_knowledge", description = "检索个人知识库，返回可追溯引用来源。")
    public DocumentSearchResponse searchPersonalKnowledge(String query) {
        return knowledgeSearch.search(query, List.of());
    }

    @Tool(name = "parse_job_description", description = "解析 JD，提取结构化岗位信息。")
    public JobPosting parseJobDescription(String rawText) {
        return parser.parse(rawText, null);
    }

    @Tool(name = "match_resume_job", description = "对比简历和 JD，生成匹配报告。")
    public JobMatchReport matchResumeJob(String resumeText, String jobText) {
        JobPosting posting = parser.parse(jobText, null);
        ResumeProfile resume = new ResumeProfile("tool-resume", resumeText, List.of(), List.of());
        return matcher.match(resume, posting);
    }

    @Tool(name = "generate_interview_questions", description = "根据岗位信息生成技术面试题。")
    public List<String> generateInterviewQuestions(String jobText) {
        return List.of(
            "请介绍一个你最熟悉的后端项目。",
            "Redis 缓存一致性如何处理？",
            "MySQL 索引失效有哪些常见场景？"
        );
    }

    @Tool(name = "generate_study_plan", description = "根据薄弱点生成学习计划。")
    public List<String> generateStudyPlan(String weakness) {
        return List.of(
            "第 1 天：梳理基础概念 - " + weakness,
            "第 2 天：结合项目复盘并整理表达",
            "第 3 天：模拟问答并修正薄弱点"
        );
    }

    @Tool(name = "analyze_project_experience", description = "提炼项目亮点、技术难点和可追问点。")
    public List<String> analyzeProjectExperience(String projectText) {
        return List.of(
            "业务背景是否清晰",
            "技术选型需要说明取舍",
            "准备性能、稳定性和排障追问"
        );
    }

    @Tool(name = "generate_resume_suggestion", description = "生成简历优化建议。")
    public List<String> generateResumeSuggestion(String resumeText) {
        return List.of(
            "用结果量化项目贡献",
            "把技术栈和业务效果放在同一条经历里",
            "为每个项目准备 STAR 表达"
        );
    }

    public Object execute(String toolName, Map<String, Object> input) {
        return switch (toolName) {
            case "search_personal_knowledge" -> searchPersonalKnowledge(String.valueOf(input.get("query")));
            case "parse_job_description" -> parseJobDescription(String.valueOf(input.get("rawText")));
            case "match_resume_job" -> matchResumeJob(String.valueOf(input.get("resumeText")), String.valueOf(input.get("jobText")));
            case "generate_interview_questions" -> generateInterviewQuestions(String.valueOf(input.get("jobText")));
            case "generate_study_plan" -> generateStudyPlan(String.valueOf(input.get("weakness")));
            case "analyze_project_experience" -> analyzeProjectExperience(String.valueOf(input.get("projectText")));
            case "generate_resume_suggestion" -> generateResumeSuggestion(String.valueOf(input.get("resumeText")));
            default -> throw new IllegalArgumentException("Tool not found: " + toolName);
        };
    }

    public List<ToolDefinition> definitions() {
        return List.of(
            definition("search_personal_knowledge", "检索个人知识库，返回可追溯引用来源。", Map.of("query", "string")),
            definition("parse_job_description", "解析 JD，提取结构化岗位信息。", Map.of("rawText", "string")),
            definition("match_resume_job", "对比简历和 JD，生成匹配报告。", Map.of("resumeText", "string", "jobText", "string")),
            definition("generate_interview_questions", "根据岗位信息生成技术面试题。", Map.of("jobText", "string")),
            definition("generate_study_plan", "根据薄弱点生成学习计划。", Map.of("weakness", "string")),
            definition("analyze_project_experience", "提炼项目亮点、技术难点和可追问点。", Map.of("projectText", "string")),
            definition("generate_resume_suggestion", "生成简历优化建议。", Map.of("resumeText", "string"))
        );
    }

    private ToolDefinition definition(String name, String description, Map<String, String> inputSchema) {
        return new ToolDefinition(name, description, inputSchema);
    }
}
