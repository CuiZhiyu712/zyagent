package com.zyagent.storage;

import com.zyagent.document.DocumentChunk;
import com.zyagent.document.DocumentMetadata;
import com.zyagent.document.DocumentRecord;
import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.KnowledgeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DocumentRecord record) {
        jdbcTemplate.update("""
            INSERT INTO document(id, filename, knowledge_type, parse_status, chunk_count, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE filename = VALUES(filename), knowledge_type = VALUES(knowledge_type), parse_status = VALUES(parse_status), chunk_count = VALUES(chunk_count)
            """,
            record.id(),
            record.filename(),
            record.knowledgeType().name(),
            record.parseStatus(),
            record.chunkCount(),
            Timestamp.valueOf(record.uploadedAt())
        );
        saveChunks(record.chunks());
    }

    public List<DocumentRecord> findAll() {
        return jdbcTemplate.query("""
            SELECT d.id, d.filename, d.knowledge_type, d.parse_status, d.uploaded_at,
                   COUNT(c.id) AS chunk_count
            FROM document d
            LEFT JOIN document_chunk c ON c.document_id = d.id
            GROUP BY d.id, d.filename, d.knowledge_type, d.parse_status, d.uploaded_at
            ORDER BY d.uploaded_at DESC
            """, (rs, rowNum) -> new DocumentRecord(
            rs.getString("id"),
            rs.getString("filename"),
            KnowledgeType.valueOf(rs.getString("knowledge_type")),
            rs.getString("parse_status"),
            rs.getInt("chunk_count"),
            rs.getTimestamp("uploaded_at").toLocalDateTime(),
            List.of()
        ));
    }

    public List<DocumentChunk> keywordSearch(String query, List<KnowledgeType> types) {
        return keywordSearchHits(query, types).stream()
            .map(hit -> new DocumentChunk(hit.documentId(), hit.chunkIndex(), hit.content()))
            .toList();
    }

    public List<DocumentSearchHit> keywordSearchHits(String query, List<KnowledgeType> types) {
        String like = "%" + (query == null ? "" : query) + "%";
        if (types == null || types.isEmpty()) {
            return jdbcTemplate.query("""
                SELECT c.document_id, d.filename, d.knowledge_type, c.chunk_index, c.content, c.vector_id
                FROM document_chunk c
                JOIN document d ON d.id = c.document_id
                WHERE c.content LIKE ?
                ORDER BY c.created_at DESC
                LIMIT 8
                """, (rs, rowNum) -> new DocumentSearchHit(
                rs.getString("document_id"),
                rs.getString("filename"),
                KnowledgeType.valueOf(rs.getString("knowledge_type")),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                0.0,
                rs.getString("vector_id")
            ), like);
        }
        List<String> names = types.stream().map(Enum::name).toList();
        String placeholders = String.join(",", names.stream().map(ignored -> "?").toList());
        Object[] args = new Object[names.size() + 1];
        args[0] = like;
        for (int i = 0; i < names.size(); i++) {
            args[i + 1] = names.get(i);
        }
        return jdbcTemplate.query("""
            SELECT c.document_id, d.filename, d.knowledge_type, c.chunk_index, c.content, c.vector_id
            FROM document_chunk c
            JOIN document d ON d.id = c.document_id
            WHERE c.content LIKE ? AND d.knowledge_type IN (%s)
            ORDER BY c.created_at DESC
            LIMIT 8
            """.formatted(placeholders), (rs, rowNum) -> new DocumentSearchHit(
            rs.getString("document_id"),
            rs.getString("filename"),
            KnowledgeType.valueOf(rs.getString("knowledge_type")),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            0.0,
            rs.getString("vector_id")
        ), args);
    }

    public int deleteById(String documentId) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
        return jdbcTemplate.update("DELETE FROM document WHERE id = ?", documentId);
    }

    public Optional<DocumentMetadata> findMetadata(String documentId) {
        List<DocumentMetadata> rows = jdbcTemplate.query("""
            SELECT id, filename, knowledge_type
            FROM document
            WHERE id = ?
            LIMIT 1
            """, (rs, rowNum) -> new DocumentMetadata(
            rs.getString("id"),
            rs.getString("filename"),
            KnowledgeType.valueOf(rs.getString("knowledge_type"))
        ), documentId);
        return rows.stream().findFirst();
    }

    private void saveChunks(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            jdbcTemplate.update("""
                INSERT INTO document_chunk(id, document_id, chunk_index, content, vector_id, metadata_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE content = VALUES(content), vector_id = VALUES(vector_id), metadata_json = VALUES(metadata_json)
                """,
                chunk.documentId() + "-" + chunk.index(),
                chunk.documentId(),
                chunk.index(),
                chunk.content(),
                chunk.documentId() + "-" + chunk.index(),
                "{}",
                Timestamp.valueOf(LocalDateTime.now())
            );
        }
    }
}
