package com.zyagent.document;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentRecord(
    String id,
    String filename,
    KnowledgeType knowledgeType,
    String parseStatus,
    int chunkCount,
    LocalDateTime uploadedAt,
    List<DocumentChunk> chunks
) {
}
