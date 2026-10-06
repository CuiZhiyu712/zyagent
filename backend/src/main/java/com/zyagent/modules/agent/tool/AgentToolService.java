package com.zyagent.modules.agent.tool;

import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.agent.port.KnowledgeSearchPort;
import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.resume.match.JobMatchReport;
import com.zyagent.modules.agent.port.JobAnalysisPort;
import com.zyagent.modules.agent.port.ResumeAdvicePort;
import com.zyagent.modules.agent.port.InterviewPracticePort;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentToolService {
    private final KnowledgeSearchPort knowledgeSearch;
    private final JobAnalysisPort jobAnalysis;
    private final ResumeAdvicePort resumeAdvice;
    private final InterviewPracticePort interviewPractice;

    public AgentToolService(KnowledgeSearchPort knowledgeSearch, JobAnalysisPort jobAnalysis,
                            ResumeAdvicePort resumeAdvice, InterviewPracticePort interviewPractice) {
        this.knowledgeSearch = knowledgeSearch;
        this.jobAnalysis = jobAnalysis;
        this.resumeAdvice = resumeAdvice;
        this.interviewPractice = interviewPractice;
    }

    @Tool(name = "search_personal_knowledge", description = "检索个人知识库，返回可追溯引用来源。")
    public DocumentSearchResponse searchPersonalKnowledge(String query) {
        return knowledgeSearch.search(query, List.of());
    }

    @Tool(name = "parse_job_description", description = "解析 JD，提取结构化岗位信息。")
    public JobPosting parseJobDescription(String rawText) {
        return jobAnalysis.parse(rawText);
    }

    @Tool(name = "match_resume_job", description = "对比简历和 JD，生成匹配报告。")
    public JobMatchReport matchResumeJob(String resumeText, String jobText) {
        return jobAnalysis.match(resumeText, jobText);
    }

    @Tool(name = "generate_interview_questions", description = "根据岗位信息生成技术面试题。")
    public List<String> generateInterviewQuestions(String jobText) {
        return interviewPractice.generateQuestions(jobText);
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
        return resumeAdvice.analyzeProject(projectText);
    }

    @Tool(name = "generate_resume_suggestion", description = "生成简历优化建议。")
    public List<String> generateResumeSuggestion(String resumeText) {
        return resumeAdvice.suggest(resumeText);
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
