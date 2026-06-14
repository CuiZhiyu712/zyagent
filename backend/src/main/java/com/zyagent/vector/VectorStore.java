package com.zyagent.vector;

import com.zyagent.document.DocumentChunk;
import com.zyagent.document.KnowledgeType;

import java.util.List;

public interface VectorStore {
    void upsert(DocumentChunk chunk, List<Float> embedding);

    default List<DocumentChunk> search(List<Float> embedding, int topK) {
        return searchHits(embedding, topK, List.of()).stream()
            .map(VectorSearchHit::chunk)
            .toList();
    }

    List<VectorSearchHit> searchHits(List<Float> embedding, int topK, List<KnowledgeType> types);

    default void deleteDocument(String documentId) {
    }
}
