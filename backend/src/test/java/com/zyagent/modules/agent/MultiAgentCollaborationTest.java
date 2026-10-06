package com.zyagent.modules.agent;

import com.zyagent.modules.knowledgebase.DocumentSearchHit;
import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.knowledgebase.KnowledgeType;
import com.zyagent.modules.job.TestAssertions;
import com.zyagent.modules.agent.skill.SkillDefinition;
import com.zyagent.modules.agent.tool.ToolResult;

import java.util.List;

public class MultiAgentCollaborationTest {
    public static void run() {
        coordinatorBuildsDeterministicSubAgentTrace();
        evaluatorExplainsToolAndRagQuality();
    }

    private static void coordinatorBuildsDeterministicSubAgentTrace() {
        SkillDefinition skill = new SkillDefinition(
            "job_analysis_skill",
            "岗位分析 Skill",
            "分析 JD 和简历匹配度",
            AgentMode.JOB_ANALYST,
            List.of("parse_job_description", "search_personal_knowledge"),
            List.of("解析岗位要求", "检索个人经历")
        );
        AgentRouteDecision route = new AgentRouteDecision(
            AgentRouteCategory.JOB,
            skill.id(),
            skill.name(),
            List.of("JD"),
            skill.toolNames(),
            "命中岗位分析"
        );
        DocumentSearchResponse references = references();
        List<ToolResult> tools = List.of(
            ToolResult.success("parse_job_description", "Java 后端岗位"),
            ToolResult.success("search_personal_knowledge", references)
        );

        CollaborationTrace trace = new MultiAgentCoordinator()
            .coordinate(route, skill, "分析这个 JD", ChatMemorySnapshot.empty(), tools, references);

        TestAssertions.equals(4, trace.agents().size(), "sub agent count");
        TestAssertions.equals(SubAgentRole.PLANNER, trace.agents().get(0).role(), "planner first");
        TestAssertions.equals(SubAgentRole.RETRIEVER, trace.agents().get(1).role(), "retriever second");
        TestAssertions.equals(SubAgentRole.EVALUATOR, trace.agents().get(2).role(), "evaluator third");
        TestAssertions.equals(SubAgentRole.REVIEWER, trace.agents().get(3).role(), "reviewer fourth");
        TestAssertions.isTrue(trace.artifacts().size() >= 4, "artifacts created");
        TestAssertions.isTrue(trace.finalReview().contains("引用"), "final review mentions evidence citation");
    }

    private static void evaluatorExplainsToolAndRagQuality() {
        CollaborationTrace trace = new MultiAgentCoordinator().coordinate(
            new AgentRouteDecision(AgentRouteCategory.RESUME, "resume_coach_skill", "简历顾问 Skill", List.of("简历"), List.of("search_personal_knowledge"), "命中简历"),
            new SkillDefinition("resume_coach_skill", "简历顾问 Skill", "优化简历", AgentMode.RESUME_COACH, List.of("search_personal_knowledge"), List.of("检索个人经历")),
            "优化我的项目经历",
            new ChatMemorySnapshot(2, "user: Java 后端"),
            List.of(ToolResult.success("search_personal_knowledge", references())),
            references()
        );

        AgentArtifact evaluation = trace.artifacts().stream()
            .filter(artifact -> artifact.producer() == SubAgentRole.EVALUATOR)
            .findFirst()
            .orElseThrow();

        TestAssertions.isTrue(evaluation.confidence() > 0.5, "evaluation confidence");
        TestAssertions.isTrue(evaluation.summary().contains("工具成功率"), "tool success explained");
        TestAssertions.isTrue(evaluation.summary().contains("RAG 命中"), "rag hit explained");
    }

    private static DocumentSearchResponse references() {
        return new DocumentSearchResponse("milvus", List.of(
            new DocumentSearchHit("doc-1", "resume.md", KnowledgeType.RESUME, 0, "Java Redis 项目", 0.91, "v1")
        ));
    }
}
