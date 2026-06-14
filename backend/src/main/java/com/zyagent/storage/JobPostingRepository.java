package com.zyagent.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.job.JobPosting;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JobPostingRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JobPostingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(JobPosting posting) {
        ensureCollectColumns();
        jdbcTemplate.update("""
            INSERT INTO job_posting(id, company, title, city, job_type, direction, skills_json, source_url, source_name, external_id, content_hash, application_status, raw_text, collected_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE company = VALUES(company), title = VALUES(title), city = VALUES(city),
                job_type = VALUES(job_type), direction = VALUES(direction), skills_json = VALUES(skills_json),
                source_url = VALUES(source_url), source_name = VALUES(source_name), external_id = VALUES(external_id),
                content_hash = VALUES(content_hash), application_status = VALUES(application_status), raw_text = VALUES(raw_text),
                last_seen_at = VALUES(last_seen_at)
            """,
            posting.id(),
            posting.company(),
            posting.title(),
            posting.city(),
            posting.jobType(),
            posting.direction(),
            json(posting.skills()),
            posting.sourceUrl(),
            posting.sourceName(),
            posting.externalId(),
            posting.contentHash(),
            posting.applicationStatus(),
            posting.rawText(),
            Timestamp.valueOf(posting.collectedAt()),
            Timestamp.valueOf(posting.lastSeenAt() == null ? posting.collectedAt() : posting.lastSeenAt())
        );
    }

    public List<JobPosting> findAll(String keyword) {
        ensureCollectColumns();
        String like = "%" + (keyword == null ? "" : keyword) + "%";
        return jdbcTemplate.query("""
            SELECT id, company, title, city, job_type, direction, skills_json, source_url, source_name, external_id, content_hash, application_status, raw_text, collected_at, last_seen_at
            FROM job_posting
            WHERE ? = '%%' OR CONCAT_WS(' ', company, title, city, skills_json, raw_text) LIKE ?
            ORDER BY collected_at DESC
            """, (rs, rowNum) -> new JobPosting(
            rs.getString("id"),
            rs.getString("company"),
            rs.getString("title"),
            rs.getString("city"),
            rs.getString("job_type"),
            rs.getString("direction"),
            List.of(),
            List.of(),
            List.of(),
            readSkills(rs.getString("skills_json")),
            rs.getString("source_url"),
            rs.getString("source_name"),
            rs.getString("external_id"),
            rs.getString("content_hash"),
            null,
            rs.getTimestamp("collected_at").toLocalDateTime(),
            rs.getTimestamp("last_seen_at") == null ? rs.getTimestamp("collected_at").toLocalDateTime() : rs.getTimestamp("last_seen_at").toLocalDateTime(),
            rs.getString("application_status"),
            rs.getString("raw_text")
        ), like, like);
    }

    public Optional<JobPosting> findById(String id) {
        ensureCollectColumns();
        List<JobPosting> postings = jdbcTemplate.query("""
            SELECT id, company, title, city, job_type, direction, skills_json, source_url, source_name, external_id, content_hash, application_status, raw_text, collected_at, last_seen_at
            FROM job_posting
            WHERE id = ?
            """, (rs, rowNum) -> new JobPosting(
            rs.getString("id"),
            rs.getString("company"),
            rs.getString("title"),
            rs.getString("city"),
            rs.getString("job_type"),
            rs.getString("direction"),
            List.of(),
            List.of(),
            List.of(),
            readSkills(rs.getString("skills_json")),
            rs.getString("source_url"),
            rs.getString("source_name"),
            rs.getString("external_id"),
            rs.getString("content_hash"),
            null,
            rs.getTimestamp("collected_at").toLocalDateTime(),
            rs.getTimestamp("last_seen_at") == null ? rs.getTimestamp("collected_at").toLocalDateTime() : rs.getTimestamp("last_seen_at").toLocalDateTime(),
            rs.getString("application_status"),
            rs.getString("raw_text")
        ), id);
        return postings.stream().findFirst();
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
        return count == null ? 0 : count;
    }

    public int deleteInvalidCollected() {
        ensureCollectColumns();
        return jdbcTemplate.update("""
            DELETE FROM job_posting
            WHERE COALESCE(source_name, '') NOT IN ('mock', 'manual', 'url')
              AND (
                  company = '未标注'
                  OR title = '未标注'
                  OR LOWER(raw_text) LIKE '%enable javascript to run this app%'
              )
            """);
    }

    public Optional<JobPosting> findByCollectKey(String externalId, String sourceUrl, String company, String title, String city, String contentHash) {
        ensureCollectColumns();
        List<JobPosting> postings = jdbcTemplate.query("""
            SELECT id, company, title, city, job_type, direction, skills_json, source_url, source_name, external_id, content_hash, application_status, raw_text, collected_at, last_seen_at
            FROM job_posting
            WHERE (? IS NOT NULL AND external_id = ?)
               OR (? IS NOT NULL AND source_url = ?)
               OR (company = ? AND title = ? AND city = ? AND content_hash = ?)
            LIMIT 1
            """, (rs, rowNum) -> new JobPosting(
            rs.getString("id"),
            rs.getString("company"),
            rs.getString("title"),
            rs.getString("city"),
            rs.getString("job_type"),
            rs.getString("direction"),
            List.of(),
            List.of(),
            List.of(),
            readSkills(rs.getString("skills_json")),
            rs.getString("source_url"),
            rs.getString("source_name"),
            rs.getString("external_id"),
            rs.getString("content_hash"),
            null,
            rs.getTimestamp("collected_at").toLocalDateTime(),
            rs.getTimestamp("last_seen_at") == null ? rs.getTimestamp("collected_at").toLocalDateTime() : rs.getTimestamp("last_seen_at").toLocalDateTime(),
            rs.getString("application_status"),
            rs.getString("raw_text")
        ), externalId, externalId, sourceUrl, sourceUrl, company, title, city, contentHash);
        return postings.stream().findFirst();
    }

    private void ensureCollectColumns() {
        addColumnIfMissing("source_name", "VARCHAR(128)");
        addColumnIfMissing("external_id", "VARCHAR(255)");
        addColumnIfMissing("content_hash", "VARCHAR(128)");
        addColumnIfMissing("last_seen_at", "TIMESTAMP NULL");
    }

    private void addColumnIfMissing(String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE job_posting ADD COLUMN " + column + " " + definition);
        } catch (RuntimeException ignored) {
            // Column already exists or the database is temporarily unavailable.
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> readSkills(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json == null ? "[]" : json);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
