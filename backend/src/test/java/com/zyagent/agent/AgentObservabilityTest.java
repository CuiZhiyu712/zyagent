package com.zyagent.agent;

import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.document.KnowledgeType;
import com.zyagent.job.TestAssertions;
import com.zyagent.tool.ToolResult;

import java.util.List;

public class AgentObservabilityTest {
    public static void run() {
        estimatesPromptAndCompletionTokens();
        summarizesRunMetricsFromToolsAndReferences();
        planStepExposesAttemptAndSummaries();
        agentResultCarriesObservabilityFields();
    }

    private static void estimatesPromptAndCompletionTokens() {
        TokenUsage usage = TokenEstimator.estimate("hello Java 后端", "生成一份学习计划");

        TestAssertions.isTrue(usage.promptTokens() > 0, "prompt tokens");
        TestAssertions.isTrue(usage.completionTokens() > 0, "completion tokens");
        TestAssertions.equals(usage.promptTokens() + usage.completionTokens(), usage.totalTokens(), "total tokens");
        TestAssertions.isTrue(usage.estimated(), "estimated marker");
    }

    private static void summarizesRunMetricsFromToolsAndReferences() {
        DocumentSearchResponse references = new DocumentSearchResponse("milvus", List.of(
            new DocumentSearchHit("doc-1", "resume.md", KnowledgeType.RESUME, 0, "Java Redis", 0.9, "v1"),
            new DocumentSearchHit("doc-2", "project.md", KnowledgeType.PROJECT, 1, "Agent RAG", 0.7, "v2")
        ));

        AgentRunMetrics metrics = AgentRunMetrics.from(
            List.of(ToolResult.success("search_personal_knowledge", references), ToolResult.failure("parse_job_description", "bad jd")),
            references,
            1
        );

        TestAssertions.equals(2, metrics.toolTotal(), "tool total");
        TestAssertions.equals(1, metrics.toolSuccess(), "tool success");
        TestAssertions.equals(1, metrics.toolFailed(), "tool failed");
        TestAssertions.equals(1, metrics.retryCount(), "retry count");
        TestAssertions.equals(2, metrics.ragHitCount(), "rag hits");
        TestAssertions.equals("milvus", metrics.ragSearchMode(), "rag mode");
        TestAssertions.isTrue(metrics.ragAverageScore() > 0.79 && metrics.ragAverageScore() < 0.81, "average score");
    }

    private static void planStepExposesAttemptAndSummaries() {
        AgentPlanStep step = AgentPlanStep.planned("search", "search_personal_knowledge")
            .withAttempt(2)
            .withInputSummary("query=Java")
            .withOutputSummary("2 hits")
            .retrying("timeout");

        TestAssertions.equals(2, step.attempt(), "attempt");
        TestAssertions.equals("query=Java", step.inputSummary(), "input summary");
        TestAssertions.equals("2 hits", step.outputSummary(), "output summary");
        TestAssertions.equals(AgentStepStatus.RETRYING, step.status(), "retrying status");
        TestAssertions.equals("timeout", step.errorMessage(), "retry reason");
    }

    private static void agentResultCarriesObservabilityFields() {
        AgentRouteDecision route = new AgentRouteDecision(AgentRouteCategory.JOB, "job_analysis_skill", "岗位分析 Skill", List.of("JD"), List.of("parse_job_description"), "命中 JD");
        TokenUsage usage = new TokenUsage(10, 5, 15, true);
        AgentRunMetrics metrics = AgentRunMetrics.from(List.of(), null, 0);
        ChatMemorySnapshot memory = new ChatMemorySnapshot(1, "user: hello");

        AgentResult result = new AgentResult(
            AgentMode.JOB_ANALYST,
            null,
            new AgentPlan(AgentMode.JOB_ANALYST, List.of()),
            List.of(),
            null,
            "answer",
            route,
            usage,
            metrics,
            memory
        );

        TestAssertions.equals(route, result.routeDecision(), "route decision");
        TestAssertions.equals(15, result.tokenUsage().totalTokens(), "token usage");
        TestAssertions.equals(metrics, result.runMetrics(), "run metrics");
        TestAssertions.equals(memory, result.memorySnapshot(), "memory snapshot");
    }
}
