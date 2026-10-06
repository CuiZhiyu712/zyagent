package com.zyagent.modules.knowledgebase;

import com.zyagent.modules.knowledgebase.retrieval.RerankOutcome;
import com.zyagent.modules.knowledgebase.retrieval.Reranker;
import com.zyagent.modules.knowledgebase.retrieval.RetrievalCandidate;
import com.zyagent.modules.knowledgebase.retrieval.RetrievalSettings;
import com.zyagent.modules.knowledgebase.retrieval.RetrievalTrace;
import com.zyagent.modules.knowledgebase.retrieval.RrfFallbackReranker;
import com.zyagent.modules.knowledgebase.retrieval.RrfFusion;
import com.zyagent.infrastructure.storage.DocumentRepository;
import com.zyagent.infrastructure.vector.EmbeddingService;
import com.zyagent.infrastructure.vector.VectorSearchHit;
import com.zyagent.infrastructure.vector.VectorStore;
import jakarta.annotation.PreDestroy;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final Tika tika = new Tika();
    private final TextChunker chunker = new TextChunker(900, 120);
    private final List<DocumentRecord> documents = new ArrayList<>();
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final Reranker reranker;
    private final RetrievalSettings settings;
    private final ExecutorService recallExecutor;

    public DocumentService(
        ObjectProvider<DocumentRepository> documentRepository,
        ObjectProvider<EmbeddingService> embeddingService,
        ObjectProvider<VectorStore> vectorStore
    ) {
        this(documentRepository, embeddingService, vectorStore, null, RetrievalSettings.defaults());
    }

    public DocumentService(
        ObjectProvider<DocumentRepository> documentRepository,
        ObjectProvider<EmbeddingService> embeddingService,
        ObjectProvider<VectorStore> vectorStore,
        ObjectProvider<Reranker> reranker
    ) {
        this(documentRepository, embeddingService, vectorStore, reranker, RetrievalSettings.defaults());
    }

    @Autowired
    public DocumentService(
        ObjectProvider<DocumentRepository> documentRepository,
        ObjectProvider<EmbeddingService> embeddingService,
        ObjectProvider<VectorStore> vectorStore,
        ObjectProvider<Reranker> reranker,
        RetrievalSettings settings
    ) {
        this.documentRepository = documentRepository.getIfAvailable();
        this.embeddingService = embeddingService.getIfAvailable();
        this.vectorStore = vectorStore.getIfAvailable();
        this.reranker = reranker == null ? new RrfFallbackReranker() : reranker.getIfAvailable(RrfFallbackReranker::new);
        this.settings = settings == null ? RetrievalSettings.defaults() : settings;
        this.recallExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "zyagent-recall");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownRecallExecutor() {
        recallExecutor.shutdownNow();
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

    /**
     * Hybrid 检索：向量与关键词两路**并行**召回，RRF 融合去重后交给可插拔 Reranker。
     *
     * <p>任一路成功即继续；两侧都失败才降级为内存检索。每路状态、耗时与候选数写入 {@link RetrievalTrace}。
     */
    public DocumentSearchResponse searchWithReferences(String query, List<KnowledgeType> types) {
        if (query == null || query.isBlank()) {
            return new DocumentSearchResponse(RetrievalTrace.noResults(settings.rankConstant(), settings.fuseLimit()), List.of());
        }
        CompletableFuture<RecallResult> vectorFuture = CompletableFuture.supplyAsync(() -> recallVector(query, types), recallExecutor);
        CompletableFuture<RecallResult> keywordFuture = CompletableFuture.supplyAsync(() -> recallKeyword(query, types), recallExecutor);
        RecallResult vector = vectorFuture.join();
        RecallResult keyword = keywordFuture.join();

        List<RetrievalCandidate> fused = RrfFusion.merge(
            vector.hits(), keyword.hits(), settings.rankConstant(), settings.fuseLimit());
        if (fused.isEmpty()) {
            return emptyResult(query, types, vector, keyword);
        }

        long rerankStart = System.nanoTime();
        RerankOutcome outcome = reranker.rerank(query, fused, settings.rerankLimit());
        long rerankLatency = elapsed(rerankStart);
        List<DocumentSearchHit> finalHits = outcome.candidates().stream().map(RetrievalCandidate::toHit).toList();

        RetrievalTrace trace = RetrievalTrace.builder(settings.rankConstant(), settings.fuseLimit())
            .vector(vector.status(), vector.latencyMs(), vector.hits().size())
            .keyword(keyword.status(), keyword.latencyMs(), keyword.hits().size())
            .fused(fused.size())
            .rerank(outcome.mode(), outcome.status(), rerankLatency, settings.rerankLimit())
            .finalRefs(finalHits.stream().map(this::ref).toList())
            .note(outcome.note())
            .build();
        return new DocumentSearchResponse(trace, finalHits);
    }

    private DocumentSearchResponse emptyResult(String query, List<KnowledgeType> types, RecallResult vector, RecallResult keyword) {
        boolean vectorUsable = RetrievalTrace.OK.equals(vector.status());
        boolean keywordUsable = RetrievalTrace.OK.equals(keyword.status());
        if (!vectorUsable && !keywordUsable) {
            List<DocumentSearchHit> memory = memorySearchHits(query, types);
            RetrievalTrace trace = RetrievalTrace.builder(settings.rankConstant(), settings.fuseLimit())
                .vector(vector.status(), vector.latencyMs(), 0)
                .keyword(keyword.status(), keyword.latencyMs(), 0)
                .fused(0)
                .rerank("none", RetrievalTrace.SKIPPED, 0L, 0)
                .finalRefs(memory.stream().map(this::ref).toList())
                .note("向量与关键词召回均不可用，降级为内存检索")
                .build();
            return new DocumentSearchResponse(trace, memory);
        }
        RetrievalTrace trace = RetrievalTrace.builder(settings.rankConstant(), settings.fuseLimit())
            .vector(vector.status(), vector.latencyMs(), vector.hits().size())
            .keyword(keyword.status(), keyword.latencyMs(), keyword.hits().size())
            .fused(0)
            .rerank("none", RetrievalTrace.SKIPPED, 0L, 0)
            .note("无召回结果")
            .build();
        return new DocumentSearchResponse(trace, List.of());
    }

    private RecallResult recallVector(String query, List<KnowledgeType> types) {
        long start = System.nanoTime();
        if (embeddingService == null || vectorStore == null) {
            return new RecallResult(RetrievalTrace.UNAVAILABLE, elapsed(start), List.of());
        }
        try {
            List<DocumentSearchHit> hits = enrichVectorHits(
                vectorStore.searchHits(embeddingService.embed(query), settings.vectorTopK(), types), types);
            return new RecallResult(RetrievalTrace.OK, elapsed(start), hits);
        } catch (RuntimeException ex) {
            log.warn("向量召回失败，保留关键词召回结果：{}", ex.getClass().getSimpleName());
            return new RecallResult(RetrievalTrace.FAILED, elapsed(start), List.of());
        }
    }

    private RecallResult recallKeyword(String query, List<KnowledgeType> types) {
        long start = System.nanoTime();
        if (documentRepository == null) {
            return new RecallResult(RetrievalTrace.UNAVAILABLE, elapsed(start), List.of());
        }
        try {
            List<DocumentSearchHit> hits = documentRepository.keywordSearchHits(query, types, settings.keywordTopK());
            return new RecallResult(RetrievalTrace.OK, elapsed(start), hits);
        } catch (RuntimeException ex) {
            log.warn("关键词召回失败，保留向量召回结果：{}", ex.getClass().getSimpleName());
            return new RecallResult(RetrievalTrace.FAILED, elapsed(start), List.of());
        }
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

    private String ref(DocumentSearchHit hit) {
        return hit.filename() + "#chunk-" + hit.chunkIndex();
    }

    private static long elapsed(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private record RecallResult(String status, long latencyMs, List<DocumentSearchHit> hits) {
    }
}
