package com.zyagent.agent;

import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EvaluatorAgent {
    public List<AgentArtifact> evaluate(SharedAgentMemory memory) {
        List<ToolResult> tools = memory.toolResults();
        int toolTotal = tools.size();
        int toolSuccess = (int) tools.stream().filter(ToolResult::success).count();
        int toolFailed = toolTotal - toolSuccess;
        double toolSuccessRate = toolTotal == 0 ? 1D : (double) toolSuccess / toolTotal;
        DocumentSearchResponse references = memory.references();
        int ragHitCount = references == null ? 0 : references.hits().size();
        double ragAverageScore = references == null
            ? 0D
            : references.hits().stream().mapToDouble(hit -> hit.score()).average().orElse(0D);
        double confidence = confidence(toolSuccessRate, ragHitCount, ragAverageScore);
        String summary = "Evaluator 评估：工具成功率 " + percent(toolSuccessRate)
            + "，RAG 命中 " + ragHitCount
            + " 条，综合置信度 " + percent(confidence) + "。";

        return List.of(new AgentArtifact(
            id("evaluation"),
            SubAgentRole.EVALUATOR,
            "evaluation",
            summary,
            Map.of(
                "toolTotal", toolTotal,
                "toolSuccess", toolSuccess,
                "toolFailed", toolFailed,
                "toolSuccessRate", toolSuccessRate,
                "ragHitCount", ragHitCount,
                "ragAverageScore", ragAverageScore,
                "missingEvidence", ragHitCount == 0
            ),
            memory.artifacts().stream()
                .flatMap(artifact -> artifact.evidenceRefs().stream())
                .distinct()
                .toList(),
            confidence
        ));
    }

    private double confidence(double toolSuccessRate, int ragHitCount, double ragAverageScore) {
        double ragScore = ragHitCount == 0 ? 0.25 : Math.min(1D, 0.45 + ragAverageScore * 0.55);
        return Math.max(0.15, Math.min(0.98, toolSuccessRate * 0.55 + ragScore * 0.45));
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private String id(String type) {
        return type + "-" + UUID.randomUUID();
    }
}
