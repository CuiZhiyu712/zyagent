package com.zyagent.storage;

import com.zyagent.document.DocumentChunk;
import com.zyagent.document.DocumentMetadata;
import com.zyagent.document.DocumentRecord;
import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.KnowledgeType;
import com.zyagent.document.retrieval.KeywordScorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
        return keywordSearchHits(query, types, 8);
    }

    /**
     * 关键词召回：按查询分词后任一 token 命中即召回，再用覆盖度评分并按相关性排序。
     *
     * <p>SQL 对 token 与知识类型做参数绑定；先取 topK 的若干倍作为候选池（按 created_at 倒序），
     * 再在内存中按覆盖度做稳定排序 —— 同分时保持 created_at 倒序，避免中文场景依赖未验证的 FULLTEXT。
     */
    public List<DocumentSearchHit> keywordSearchHits(String query, List<KnowledgeType> types, int limit) {
        List<String> tokens = KeywordScorer.tokenize(query);
        if (tokens.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT c.document_id, d.filename, d.knowledge_type, c.chunk_index, c.content, c.vector_id
            FROM document_chunk c
            JOIN document d ON d.id = c.document_id
            WHERE (
            """);
        for (int i = 0; i < tokens.size(); i++) {
            sql.append(i == 0 ? "" : " OR ").append("c.content LIKE ?");
            args.add("%" + KeywordScorer.escapeLike(tokens.get(i)) + "%");
        }
        sql.append(")");
        if (types != null && !types.isEmpty()) {
            sql.append(" AND d.knowledge_type IN (")
                .append(String.join(",", types.stream().map(ignored -> "?").toList()))
                .append(")");
            types.forEach(type -> args.add(type.name()));
        }
        sql.append(" ORDER BY c.created_at DESC LIMIT ?");
        args.add(Math.max(limit, limit * 3));

        List<DocumentSearchHit> pool = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DocumentSearchHit(
            rs.getString("document_id"),
            rs.getString("filename"),
            KnowledgeType.valueOf(rs.getString("knowledge_type")),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            KeywordScorer.coverage(tokens, rs.getString("content")),
            rs.getString("vector_id")
        ), args.toArray());

        List<DocumentSearchHit> scored = new ArrayList<>(pool);
        scored.sort(Comparator.comparingDouble(DocumentSearchHit::score).reversed());
        return scored.size() > limit ? scored.subList(0, limit) : scored;
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
