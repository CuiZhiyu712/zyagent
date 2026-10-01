package com.zyagent.agent;

import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.DocumentSearchResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RetrieverAgent {
    public List<AgentArtifact> retrieve(SharedAgentMemory memory) {
        DocumentSearchResponse references = memory.references();
        if (references == null || references.hits().isEmpty()) {
            return List.of(new AgentArtifact(
                id("evidence"),
                SubAgentRole.RETRIEVER,
                "evidence",
                "Retriever 未获得 RAG 命中，后续回答需要说明证据不足并依赖已有上下文。",
                Map.of(
                    "searchMode", references == null ? "none" : references.searchMode(),
                    "hitCount", 0,
                    "recentMessageCount", memory.memorySnapshot().recentMessageCount()
                ),
                List.of(),
                0.25
            ));
        }

        List<String> evidenceRefs = references.hits().stream()
            .map(this::ref)
            .toList();
        double averageScore = references.hits().stream()
            .mapToDouble(DocumentSearchHit::score)
            .average()
            .orElse(0D);
        return List.of(new AgentArtifact(
            id("evidence"),
            SubAgentRole.RETRIEVER,
            "evidence",
            "Retriever 获取 " + references.hits().size() + " 条 RAG 命中，搜索模式 " + references.searchMode() + "。",
            Map.of(
                "searchMode", references.searchMode(),
                "hitCount", references.hits().size(),
                "averageScore", averageScore,
                "topEvidence", references.hits().stream().limit(3).map(this::preview).toList()
            ),
            evidenceRefs,
            Math.min(0.95, Math.max(0.35, averageScore))
        ));
    }

    private Map<String, Object> preview(DocumentSearchHit hit) {
        return Map.of(
            "filename", hit.filename(),
            "knowledgeType", hit.knowledgeType().name(),
            "score", hit.score(),
            "content", summarize(hit.content())
        );
    }

    private String ref(DocumentSearchHit hit) {
        return hit.filename() + "#chunk-" + hit.chunkIndex();
    }

    private String summarize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String id(String type) {
        return type + "-" + UUID.randomUUID();
    }
}
