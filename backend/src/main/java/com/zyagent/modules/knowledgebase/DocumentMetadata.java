package com.zyagent.modules.knowledgebase;

public record DocumentMetadata(
    String id,
    String filename,
    KnowledgeType knowledgeType
) {
}
