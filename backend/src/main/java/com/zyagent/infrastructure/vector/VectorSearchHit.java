package com.zyagent.infrastructure.vector;

import com.zyagent.modules.knowledgebase.DocumentChunk;

public record VectorSearchHit(
    DocumentChunk chunk,
    double score,
    String vectorId
) {
}
