package com.zyagent.document;

import com.zyagent.document.retrieval.RetrievalTrace;

import java.util.List;

/**
 * 检索响应。{@code searchMode} 保持向后兼容，{@code trace} 提供每路召回与重排的结构化元信息。
 */
public record DocumentSearchResponse(
    String searchMode,
    List<DocumentSearchHit> hits,
    RetrievalTrace trace
) {
    public DocumentSearchResponse(String searchMode, List<DocumentSearchHit> hits) {
        this(searchMode, hits, null);
    }

    public DocumentSearchResponse(RetrievalTrace trace, List<DocumentSearchHit> hits) {
        this(trace == null ? "unknown" : trace.searchMode(), hits, trace);
    }

    public List<DocumentChunk> chunks() {
        return hits.stream()
            .map(hit -> new DocumentChunk(hit.documentId(), hit.chunkIndex(), hit.content()))
            .toList();
    }
}
