package com.zyagent.document;

public record DocumentMetadata(
    String id,
    String filename,
    KnowledgeType knowledgeType
) {
}
