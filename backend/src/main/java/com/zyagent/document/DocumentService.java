package com.zyagent.document;

import com.zyagent.storage.DocumentRepository;
import com.zyagent.vector.EmbeddingService;
import com.zyagent.vector.VectorSearchHit;
import com.zyagent.vector.VectorStore;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final Tika tika = new Tika();
    private final TextChunker chunker = new TextChunker(900, 120);
    private final List<DocumentRecord> documents = new ArrayList<>();
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DocumentService(
        ObjectProvider<DocumentRepository> documentRepository,
        ObjectProvider<EmbeddingService> embeddingService,
        ObjectProvider<VectorStore> vectorStore
    ) {
        this.documentRepository = documentRepository.getIfAvailable();
        this.embeddingService = embeddingService.getIfAvailable();
        this.vectorStore = vectorStore.getIfAvailable();
    }

    public DocumentRecord upload(MultipartFile file, KnowledgeType knowledgeType) {
        try {
            String id = UUID.randomUUID().toString();
            String text = tika.parseToString(file.getInputStream());
            List<DocumentChunk> chunks = chunker.chunk(id, text);
            boolean vectorized = indexChunks(chunks);
            String parseStatus = vectorized ? "VECTORIZED" : "VECTOR_FAILED";
            DocumentRecord record = new DocumentRecord(id, file.getOriginalFilename(), knowledgeType, parseStatus, chunks.size(), LocalDateTime.now(), chunks);
            documents.add(record);
            if (documentRepository != null) {
                documentRepository.save(record);
            }
            return record;
        } catch (IOException | TikaException ex) {
            throw new IllegalArgumentException("文件解析失败：" + ex.getMessage());
        }
    }

    public List<DocumentRecord> list() {
        if (documentRepository != null) {
            try {
                return documentRepository.findAll();
            } catch (RuntimeException ignored) {
                return List.copyOf(documents);
            }
        }
        return List.copyOf(documents);
    }

    public boolean delete(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return false;
        }
        if (documentRepository != null) {
            try {
                int deleted = documentRepository.deleteById(documentId);
                if (deleted > 0) {
                    documents.removeIf(doc -> doc.id().equals(documentId));
                    deleteVectors(documentId);
                    return true;
                }
                return false;
            } catch (RuntimeException ex) {
                throw new IllegalStateException("文档删除失败：" + ex.getMessage(), ex);
            }
        }
        boolean removed = documents.removeIf(doc -> doc.id().equals(documentId));
        if (removed) {
            deleteVectors(documentId);
        }
        return removed;
    }

    public List<DocumentChunk> search(String query, List<KnowledgeType> types) {
        return searchWithReferences(query, types).chunks();
    }

    public DocumentSearchResponse searchWithReferences(String query, List<KnowledgeType> types) {
        if (embeddingService != null && vectorStore != null) {
            try {
                int topK = types == null || types.isEmpty() ? 8 : 24;
                List<DocumentSearchHit> hits = enrichVectorHits(vectorStore.searchHits(embeddingService.embed(query), topK, types), types);
                if (!hits.isEmpty()) {
                    return new DocumentSearchResponse("milvus", hits);
                }
            } catch (RuntimeException ignored) {
                // Fallback below keeps the chat path usable when Milvus is unavailable.
            }
        }
        if (documentRepository != null) {
            try {
                return new DocumentSearchResponse("keyword_fallback", documentRepository.keywordSearchHits(query, types));
            } catch (RuntimeException ignored) {
                return new DocumentSearchResponse("memory_fallback", memorySearchHits(query, types));
            }
        }
        return new DocumentSearchResponse("memory_fallback", memorySearchHits(query, types));
    }

    private boolean indexChunks(List<DocumentChunk> chunks) {
        if (embeddingService == null || vectorStore == null) {
            return false;
        }
        for (DocumentChunk chunk : chunks) {
            try {
                vectorStore.upsert(chunk, embeddingService.embed(chunk.content()));
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return true;
    }

    private void deleteVectors(String documentId) {
        if (vectorStore == null) {
            return;
        }
        try {
            vectorStore.deleteDocument(documentId);
        } catch (RuntimeException ignored) {
            // MySQL metadata deletion is authoritative for the UI; stale vectors are ignored once metadata is gone.
        }
    }

    private List<DocumentSearchHit> memorySearchHits(String query, List<KnowledgeType> types) {
        String lower = query == null ? "" : query.toLowerCase();
        return documents.stream()
            .filter(doc -> types == null || types.isEmpty() || types.contains(doc.knowledgeType()))
            .flatMap(doc -> doc.chunks().stream()
                .filter(chunk -> chunk.content().toLowerCase().contains(lower))
                .map(chunk -> new DocumentSearchHit(
                    doc.id(),
                    doc.filename(),
                    doc.knowledgeType(),
                    chunk.index(),
                    chunk.content(),
                    0.0,
                    chunk.documentId() + "-" + chunk.index()
                )))
            .limit(8)
            .toList();
    }

    private List<DocumentSearchHit> enrichVectorHits(List<VectorSearchHit> vectorHits, List<KnowledgeType> types) {
        return vectorHits.stream()
            .map(hit -> documentRepository == null
                ? fallbackHit(hit)
                : documentRepository.findMetadata(hit.chunk().documentId())
                    .map(metadata -> new DocumentSearchHit(
                        metadata.id(),
                        metadata.filename(),
                        metadata.knowledgeType(),
                        hit.chunk().index(),
                        hit.chunk().content(),
                        hit.score(),
                        hit.vectorId()
                    ))
                    .orElseGet(() -> fallbackHit(hit)))
            .filter(hit -> types == null || types.isEmpty() || types.contains(hit.knowledgeType()))
            .limit(8)
            .toList();
    }

    private DocumentSearchHit fallbackHit(VectorSearchHit hit) {
        return new DocumentSearchHit(
            hit.chunk().documentId(),
            hit.chunk().documentId(),
            KnowledgeType.STUDY,
            hit.chunk().index(),
            hit.chunk().content(),
            hit.score(),
            hit.vectorId()
        );
    }
}
