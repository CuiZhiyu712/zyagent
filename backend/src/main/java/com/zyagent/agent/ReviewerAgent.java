package com.zyagent.agent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReviewerAgent {
    public List<AgentArtifact> review(SharedAgentMemory memory) {
        boolean hasEvidence = memory.artifacts().stream()
            .anyMatch(artifact -> artifact.producer() == SubAgentRole.RETRIEVER && !artifact.evidenceRefs().isEmpty());
        boolean hasFailedTool = memory.toolResults().stream().anyMatch(result -> !result.success());
        String summary = hasEvidence
            ? "Reviewer 复核：最终回答需要引用 Retriever 证据，并说明 Evaluator 的置信度。"
            : "Reviewer 复核：缺少可引用证据，最终回答需要明确证据不足并给出下一步建议。";
        if (hasFailedTool) {
            summary += " 存在失败工具，回答需要说明降级处理。";
        }

        return List.of(new AgentArtifact(
            id("review"),
            SubAgentRole.REVIEWER,
            "review",
            summary,
            Map.of(
                "mustReferenceIntermediateResults", true,
                "hasEvidence", hasEvidence,
                "hasFailedTool", hasFailedTool,
                "answerConstraints", List.of(
                    "引用 Planner 的任务拆解",
                    "引用 Retriever 的证据来源",
                    "引用 Evaluator 的质量评估",
                    "遵守 Reviewer 的风险提示"
                )
            ),
            memory.artifacts().stream()
                .flatMap(artifact -> artifact.evidenceRefs().stream())
                .distinct()
                .toList(),
            hasEvidence ? 0.86 : 0.55
        ));
    }

    private String id(String type) {
        return type + "-" + UUID.randomUUID();
    }
}
