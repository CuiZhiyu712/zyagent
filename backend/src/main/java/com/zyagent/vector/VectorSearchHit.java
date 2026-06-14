package com.zyagent.vector;

import com.zyagent.document.DocumentChunk;

public record VectorSearchHit(
    DocumentChunk chunk,
    double score,
    String vectorId
) {
}
