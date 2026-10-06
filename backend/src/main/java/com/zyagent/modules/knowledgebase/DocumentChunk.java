package com.zyagent.modules.knowledgebase;

public record DocumentChunk(
    String documentId,
    int index,
    String content
) {
}
