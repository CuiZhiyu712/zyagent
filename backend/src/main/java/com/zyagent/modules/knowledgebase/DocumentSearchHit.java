package com.zyagent.modules.knowledgebase;

public record DocumentSearchHit(
    String documentId,
    String filename,
    KnowledgeType knowledgeType,
    int chunkIndex,
    String content,
    double score,
    String vectorId
) {
}
