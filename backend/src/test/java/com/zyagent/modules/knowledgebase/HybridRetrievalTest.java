package com.zyagent.modules.knowledgebase;

import com.zyagent.modules.knowledgebase.retrieval.RetrievalSettings;
import com.zyagent.modules.knowledgebase.retrieval.RetrievalTrace;
import com.zyagent.infrastructure.storage.DocumentRepository;
import com.zyagent.infrastructure.vector.EmbeddingService;
import com.zyagent.infrastructure.vector.VectorSearchHit;
import com.zyagent.infrastructure.vector.VectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalTest {
    @Test
    void fusesBothChannelsAndMarksRrfFallback() {
        DocumentSearchResponse response = service(new FakeRepository(List.of(keywordHit())), vectorStore(0.9), true)
            .searchWithReferences("Redis 缓存", List.of());

        assertFalse(response.hits().isEmpty(), "fused hits present");
        assertEquals(RetrievalTrace.OK, response.trace().vectorStatus());
        assertEquals(RetrievalTrace.OK, response.trace().keywordStatus());
        assertEquals("hybrid:ok+ok;rerank=rrf_fallback", response.searchMode());
    }

    @Test
    void keepsKeywordResultsWhenVectorRecallFails() {
        DocumentSearchResponse response = service(new FakeRepository(List.of(keywordHit())), failingVectorStore(), true)
            .searchWithReferences("Redis 缓存", List.of());

        assertEquals(RetrievalTrace.FAILED, response.trace().vectorStatus(), "vector failure recorded");
        assertEquals(RetrievalTrace.OK, response.trace().keywordStatus(), "keyword still healthy");
        assertFalse(response.hits().isEmpty(), "keyword results survive vector outage");
    }

    @Test
    void degradesToMemoryWhenBothChannelsUnavailable() {
        DocumentService service = new DocumentService(
            provider(null), provider(null), provider(null), null, RetrievalSettings.defaults());

        DocumentSearchResponse response = service.searchWithReferences("Redis", List.of());

        assertEquals("hybrid:unavailable+unavailable;rerank=none", response.searchMode());
        assertTrue(response.trace().note().contains("降级为内存检索"), "degradation recorded in trace");
    }

    @Test
    void emptyQueryShortCircuitsWithoutRecall() {
        DocumentSearchResponse response = service(new FakeRepository(List.of(keywordHit())), vectorStore(0.9), true)
            .searchWithReferences("   ", List.of());

        assertTrue(response.hits().isEmpty(), "empty query returns no hits");
        assertEquals("hybrid:ok+ok;rerank=none", response.searchMode());
        assertEquals("无召回结果", response.trace().note());
    }

    private DocumentService service(DocumentRepository repository, VectorStore vectorStore, boolean withEmbedding) {
        return new DocumentService(
            provider(repository),
            provider(withEmbedding ? embedding() : null),
            provider(vectorStore),
            null,
            RetrievalSettings.defaults());
    }

    private static DocumentSearchHit keywordHit() {
        return new DocumentSearchHit("kw-doc", "notes.md", KnowledgeType.STUDY, 0, "Redis 缓存一致性", 0.5, "kw-0");
    }

    private static EmbeddingService embedding() {
        return new EmbeddingService() {
            @Override
            public int dimension() {
                return 3;
            }

            @Override
            public List<Float> embed(String text) {
                return List.of(0.1f, 0.2f, 0.3f);
            }
        };
    }

    private static VectorStore vectorStore(double score) {
        return new VectorStore() {
            @Override
            public void upsert(DocumentChunk chunk, List<Float> embedding) {
            }

            @Override
            public List<VectorSearchHit> searchHits(List<Float> embedding, int topK, List<KnowledgeType> types) {
                return List.of(new VectorSearchHit(new DocumentChunk("vec-doc", 0, "Redis 缓存详解"), score, "vec-0"));
            }
        };
    }

    private static VectorStore failingVectorStore() {
        return new VectorStore() {
            @Override
            public void upsert(DocumentChunk chunk, List<Float> embedding) {
            }

            @Override
            public List<VectorSearchHit> searchHits(List<Float> embedding, int topK, List<KnowledgeType> types) {
                throw new IllegalStateException("milvus down");
            }
        };
    }

    private static final class FakeRepository extends DocumentRepository {
        private final List<DocumentSearchHit> hits;

        FakeRepository(List<DocumentSearchHit> hits) {
            super(null);
            this.hits = hits;
        }

        @Override
        public List<DocumentSearchHit> keywordSearchHits(String query, List<KnowledgeType> types, int limit) {
            return hits;
        }

        @Override
        public Optional<DocumentMetadata> findMetadata(String documentId) {
            return Optional.empty();
        }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new SimpleObjectProvider<>(value);
    }

    private static final class SimpleObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        SimpleObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return getObject();
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return value == null ? List.<T>of().iterator() : List.of(value).iterator();
        }

        @Override
        public Stream<T> stream() {
            return value == null ? Stream.empty() : Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
