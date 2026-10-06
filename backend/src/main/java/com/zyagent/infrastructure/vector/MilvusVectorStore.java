package com.zyagent.infrastructure.vector;

import com.zyagent.infrastructure.config.ZyagentProperties;
import com.zyagent.modules.knowledgebase.DocumentChunk;
import com.zyagent.modules.knowledgebase.KnowledgeType;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MilvusVectorStore implements VectorStore {
    private static final String ID_FIELD = "chunk_id";
    private static final String DOCUMENT_FIELD = "document_id";
    private static final String INDEX_FIELD = "chunk_index";
    private static final String CONTENT_FIELD = "content";
    private static final String VECTOR_FIELD = "embedding";

    private final String collection;
    private final EmbeddingService embeddingService;
    private final MilvusServiceClient client;
    private volatile boolean ready;

    public MilvusVectorStore(ZyagentProperties properties, EmbeddingService embeddingService) {
        this.collection = properties.milvus().collection();
        this.embeddingService = embeddingService;
        this.client = new MilvusServiceClient(ConnectParam.newBuilder()
            .withHost(properties.milvus().host())
            .withPort(properties.milvus().port())
            .build());
    }

    @Override
    public void upsert(DocumentChunk chunk, List<Float> embedding) {
        ensureCollection();
        List<InsertParam.Field> fields = List.of(
            new InsertParam.Field(ID_FIELD, List.of(vectorId(chunk))),
            new InsertParam.Field(DOCUMENT_FIELD, List.of(chunk.documentId())),
            new InsertParam.Field(INDEX_FIELD, List.of((long) chunk.index())),
            new InsertParam.Field(CONTENT_FIELD, List.of(chunk.content())),
            new InsertParam.Field(VECTOR_FIELD, List.of(embedding))
        );
        R<?> result = client.insert(InsertParam.newBuilder()
            .withCollectionName(collection)
            .withFields(fields)
            .build());
        if (result.getStatus() != 0) {
            throw new IllegalStateException("Milvus insert failed: " + result.getMessage());
        }
    }

    @Override
    public List<VectorSearchHit> searchHits(List<Float> embedding, int topK, List<KnowledgeType> types) {
        ensureCollection();
        R<SearchResults> result = client.search(SearchParam.newBuilder()
            .withCollectionName(collection)
            .withMetricType(MetricType.COSINE)
            .withVectorFieldName(VECTOR_FIELD)
            .withFloatVectors(List.of(embedding))
            .withTopK(topK)
            .withOutFields(List.of(DOCUMENT_FIELD, INDEX_FIELD, CONTENT_FIELD))
            .withParams("{\"nprobe\":10}")
            .build());
        if (result.getStatus() != 0) {
            throw new IllegalStateException("Milvus search failed: " + result.getMessage());
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
        List<VectorSearchHit> chunks = new ArrayList<>();
        for (SearchResultsWrapper.IDScore score : wrapper.getIDScore(0)) {
            DocumentChunk chunk = new DocumentChunk(
                String.valueOf(score.get(DOCUMENT_FIELD)),
                ((Number) score.get(INDEX_FIELD)).intValue(),
                String.valueOf(score.get(CONTENT_FIELD))
            );
            chunks.add(new VectorSearchHit(
                chunk,
                score.getScore(),
                score.getStrID()
            ));
        }
        return chunks;
    }

    @Override
    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        ensureCollection();
        R<?> result = client.delete(DeleteParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(DOCUMENT_FIELD + " == \"" + documentId.replace("\"", "\\\"") + "\"")
            .build());
        if (result.getStatus() != 0) {
            throw new IllegalStateException("Milvus delete failed: " + result.getMessage());
        }
    }

    private synchronized void ensureCollection() {
        if (ready) {
            return;
        }
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
            .withCollectionName(collection)
            .build());
        if (exists.getStatus() != 0) {
            throw new IllegalStateException("Milvus hasCollection failed: " + exists.getMessage());
        }
        if (!Boolean.TRUE.equals(exists.getData())) {
            createCollection();
            createIndex();
        }
        client.loadCollection(LoadCollectionParam.newBuilder()
            .withCollectionName(collection)
            .withSyncLoad(Boolean.TRUE)
            .withSyncLoadWaitingTimeout(20L)
            .build());
        ready = true;
    }

    private void createCollection() {
        List<FieldType> fields = List.of(
            FieldType.newBuilder().withName(ID_FIELD).withDataType(DataType.VarChar).withPrimaryKey(true).withMaxLength(128).build(),
            FieldType.newBuilder().withName(DOCUMENT_FIELD).withDataType(DataType.VarChar).withMaxLength(128).build(),
            FieldType.newBuilder().withName(INDEX_FIELD).withDataType(DataType.Int64).build(),
            FieldType.newBuilder().withName(CONTENT_FIELD).withDataType(DataType.VarChar).withMaxLength(8192).build(),
            FieldType.newBuilder().withName(VECTOR_FIELD).withDataType(DataType.FloatVector).withDimension(embeddingService.dimension()).build()
        );
        R<?> result = client.createCollection(CreateCollectionParam.newBuilder()
            .withCollectionName(collection)
            .withFieldTypes(fields)
            .build());
        if (result.getStatus() != 0) {
            throw new IllegalStateException("Milvus createCollection failed: " + result.getMessage());
        }
    }

    private void createIndex() {
        R<?> result = client.createIndex(CreateIndexParam.newBuilder()
            .withCollectionName(collection)
            .withFieldName(VECTOR_FIELD)
            .withIndexType(IndexType.AUTOINDEX)
            .withMetricType(MetricType.COSINE)
            .withSyncMode(Boolean.TRUE)
            .build());
        if (result.getStatus() != 0) {
            throw new IllegalStateException("Milvus createIndex failed: " + result.getMessage());
        }
    }

    private String vectorId(DocumentChunk chunk) {
        return chunk.documentId() + "-" + chunk.index();
    }
}
