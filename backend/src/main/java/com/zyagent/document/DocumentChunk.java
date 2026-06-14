package com.zyagent.document;

public record DocumentChunk(
    String documentId,
    int index,
    String content
) {
}
