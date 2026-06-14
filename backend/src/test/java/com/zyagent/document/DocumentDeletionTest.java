package com.zyagent.document;

import com.zyagent.job.TestAssertions;
import com.zyagent.storage.DocumentRepository;
import com.zyagent.vector.VectorSearchHit;
import com.zyagent.vector.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Iterator;
import java.util.stream.Stream;

public class DocumentDeletionTest {
    public static void run() {
        returnsFalseWhenRepositoryDoesNotDelete();
        propagatesRepositoryDeleteFailure();
        deletesVectorsAfterRepositoryDelete();
    }

    private static void returnsFalseWhenRepositoryDoesNotDelete() {
        DocumentService service = new DocumentService(
            provider(new FakeDocumentRepository(0, false)),
            provider(null),
            provider(null)
        );

        boolean deleted = service.delete("missing-doc");

        TestAssertions.isTrue(!deleted, "missing repository document is not deleted");
    }

    private static void propagatesRepositoryDeleteFailure() {
        DocumentService service = new DocumentService(
            provider(new FakeDocumentRepository(0, true)),
            provider(null),
            provider(null)
        );

        try {
            service.delete("doc-1");
            throw new AssertionError("expected delete failure");
        } catch (IllegalStateException ex) {
            TestAssertions.isTrue(ex.getMessage().contains("文档删除失败"), "delete failure message");
        }
    }

    private static void deletesVectorsAfterRepositoryDelete() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        DocumentService service = new DocumentService(
            provider(new FakeDocumentRepository(1, false)),
            provider(null),
            provider(vectorStore)
        );

        boolean deleted = service.delete("doc-1");

        TestAssertions.isTrue(deleted, "repository document is deleted");
        TestAssertions.equals("doc-1", vectorStore.deletedDocumentId, "deleted vector document id");
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new SimpleObjectProvider<>(value);
    }

    private static class FakeDocumentRepository extends DocumentRepository {
        private final int deletedRows;
        private final boolean fail;

        FakeDocumentRepository(int deletedRows, boolean fail) {
            super(null);
            this.deletedRows = deletedRows;
            this.fail = fail;
        }

        @Override
        public int deleteById(String documentId) {
            if (fail) {
                throw new IllegalStateException("database unavailable");
            }
            return deletedRows;
        }
    }

    private static class FakeVectorStore implements VectorStore {
        private String deletedDocumentId;

        @Override
        public void upsert(DocumentChunk chunk, List<Float> embedding) {
        }

        @Override
        public List<VectorSearchHit> searchHits(List<Float> embedding, int topK, List<KnowledgeType> types) {
            return List.of();
        }

        @Override
        public void deleteDocument(String documentId) {
            this.deletedDocumentId = documentId;
        }
    }

    private static class SimpleObjectProvider<T> implements ObjectProvider<T> {
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
