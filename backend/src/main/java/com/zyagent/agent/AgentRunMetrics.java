package com.zyagent.agent;

import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.tool.ToolResult;

import java.util.List;

public record AgentRunMetrics(
    int toolTotal,
    int toolSuccess,
    int toolFailed,
    int retryCount,
    double toolSuccessRate,
    String ragSearchMode,
    int ragHitCount,
    double ragMaxScore,
    double ragAverageScore
) {
    public static AgentRunMetrics from(List<ToolResult> tools, DocumentSearchResponse references, int retryCount) {
        int total = tools == null ? 0 : tools.size();
        int success = tools == null ? 0 : (int) tools.stream().filter(ToolResult::success).count();
        int failed = Math.max(0, total - success);
        double successRate = total == 0 ? 1.0 : success * 1.0 / total;
        List<DocumentSearchHit> hits = references == null || references.hits() == null ? List.of() : references.hits();
        double max = hits.stream().mapToDouble(DocumentSearchHit::score).max().orElse(0.0);
        double average = hits.stream().mapToDouble(DocumentSearchHit::score).average().orElse(0.0);
        return new AgentRunMetrics(
            total,
            success,
            failed,
            Math.max(0, retryCount),
            successRate,
            references == null ? "none" : references.searchMode(),
            hits.size(),
            max,
            average
        );
    }
}
