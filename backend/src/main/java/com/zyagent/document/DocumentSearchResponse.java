package com.zyagent.document;

import java.util.List;

public record DocumentSearchResponse(
    String searchMode,
    List<DocumentSearchHit> hits
) {
    public List<DocumentChunk> chunks() {
        return hits.stream()
            .map(hit -> new DocumentChunk(hit.documentId(), hit.chunkIndex(), hit.content()))
            .toList();
    }
}
