package com.zyagent.infrastructure.storage;

import com.zyagent.modules.job.JobCollectLog;
import com.zyagent.modules.job.JobCollectorStore;
import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.job.JobSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcJobCollectorStore implements JobCollectorStore {
    private final JdbcTemplate jdbcTemplate;
    private final JobPostingRepository jobPostingRepository;

    public JdbcJobCollectorStore(JdbcTemplate jdbcTemplate, JobPostingRepository jobPostingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobPostingRepository = jobPostingRepository;
        ensureTables();
    }

    @Override
    public List<JobSource> listSources() {
        return jdbcTemplate.query("""
            SELECT id, company, name, url, enabled, keywords, last_collected_at, last_status,
                   source_type, search_url_template, list_item_selector, detail_url_selector, enabled_detail_fetch, max_detail_pages
            FROM job_source
            ORDER BY company, name
            """, (rs, rowNum) -> new JobSource(
            rs.getString("id"),
            rs.getString("company"),
            rs.getString("name"),
            rs.getString("url"),
            rs.getBoolean("enabled"),
            rs.getString("keywords"),
            rs.getTimestamp("last_collected_at") == null ? null : rs.getTimestamp("last_collected_at").toLocalDateTime(),
            rs.getString("last_status"),
            rs.getString("source_type"),
            rs.getString("search_url_template"),
            rs.getString("list_item_selector"),
            rs.getString("detail_url_selector"),
            rs.getBoolean("enabled_detail_fetch"),
            rs.getInt("max_detail_pages")
        ));
    }

    @Override
    public JobSource saveSource(JobSource source) {
        jdbcTemplate.update("""
            INSERT INTO job_source(id, company, name, url, enabled, keywords, last_collected_at, last_status,
                source_type, search_url_template, list_item_selector, detail_url_selector, enabled_detail_fetch, max_detail_pages)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE company = VALUES(company), name = VALUES(name), url = VALUES(url),
                enabled = VALUES(enabled), keywords = VALUES(keywords), last_collected_at = VALUES(last_collected_at),
                last_status = VALUES(last_status), source_type = VALUES(source_type), search_url_template = VALUES(search_url_template),
                list_item_selector = VALUES(list_item_selector), detail_url_selector = VALUES(detail_url_selector),
                enabled_detail_fetch = VALUES(enabled_detail_fetch), max_detail_pages = VALUES(max_detail_pages)
            """,
            source.id(),
            source.company(),
            source.name(),
            source.url(),
            source.enabled(),
            source.keywords(),
            source.lastCollectedAt() == null ? null : Timestamp.valueOf(source.lastCollectedAt()),
            source.lastStatus(),
            source.normalizedSourceType(),
            source.searchUrlTemplate(),
            source.listItemSelector(),
            source.detailUrlSelector(),
            source.enabledDetailFetch(),
            source.normalizedMaxDetailPages()
        );
        return source;
    }

    @Override
    public void updateSourceStatus(String sourceId, String status) {
        jdbcTemplate.update("""
            UPDATE job_source
            SET last_collected_at = ?, last_status = ?
            WHERE id = ?
            """, Timestamp.valueOf(LocalDateTime.now()), status, sourceId);
    }

    @Override
    public UpsertResult upsertCollectedJob(JobPosting posting, String sourceName, String externalId, String contentHash) {
        Optional<JobPosting> existing = jobPostingRepository.findByCollectKey(
            externalId,
            externalId == null || externalId.isBlank() ? posting.sourceUrl() : null,
            posting.company(),
            posting.title(),
            posting.city(),
            contentHash
        );
        JobPosting value = existing
            .map(old -> new JobPosting(
                old.id(),
                posting.company(),
                posting.title(),
                posting.city(),
                posting.jobType(),
                posting.direction(),
                posting.responsibilities(),
                posting.requirements(),
                posting.bonusItems(),
                posting.skills(),
                posting.sourceUrl(),
                sourceName,
                externalId,
                contentHash,
                posting.publishedDate(),
                old.collectedAt(),
                LocalDateTime.now(),
                posting.applicationStatus(),
                posting.rawText()
            ))
            .orElseGet(() -> posting.withCollectMetadata(sourceName, externalId, contentHash));
        jobPostingRepository.save(value);
        return existing.isPresent() ? UpsertResult.UPDATED : UpsertResult.ADDED;
    }

    @Override
    public void saveLog(JobCollectLog log) {
        jdbcTemplate.update("""
            INSERT INTO job_collect_log(id, source_id, source_name, started_at, finished_at, status, added, updated, skipped, failed, error_message,
                searched, detail_fetched, detail_failed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            log.id(),
            log.sourceId(),
            log.sourceName(),
            Timestamp.valueOf(log.startedAt()),
            Timestamp.valueOf(log.finishedAt()),
            log.status(),
            log.added(),
            log.updated(),
            log.skipped(),
            log.failed(),
            log.errorMessage(),
            log.searched(),
            log.detailFetched(),
            log.detailFailed()
        );
    }

    @Override
    public List<JobCollectLog> listLogs() {
        return jdbcTemplate.query("""
            SELECT id, source_id, source_name, started_at, finished_at, status, added, updated, skipped, failed, error_message,
                   searched, detail_fetched, detail_failed
            FROM job_collect_log
            ORDER BY started_at DESC
            LIMIT 50
            """, (rs, rowNum) -> new JobCollectLog(
            rs.getString("id"),
            rs.getString("source_id"),
            rs.getString("source_name"),
            rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("finished_at").toLocalDateTime(),
            rs.getString("status"),
            rs.getInt("added"),
            rs.getInt("updated"),
            rs.getInt("skipped"),
            rs.getInt("failed"),
            rs.getString("error_message"),
            rs.getInt("searched"),
            rs.getInt("detail_fetched"),
            rs.getInt("detail_failed")
        ));
    }

    @Override
    public Optional<JobSource> findSource(String id) {
        return listSources().stream().filter(source -> source.id().equals(id)).findFirst();
    }

    @Override
    public int deleteInvalidCollectedJobs() {
        return jobPostingRepository.deleteInvalidCollected();
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS job_source (
                id VARCHAR(64) PRIMARY KEY,
                company VARCHAR(128) NOT NULL,
                name VARCHAR(255) NOT NULL,
                url VARCHAR(1024) NOT NULL,
                enabled BOOLEAN NOT NULL,
                keywords VARCHAR(512),
                last_collected_at TIMESTAMP NULL,
                last_status VARCHAR(64),
                source_type VARCHAR(64) DEFAULT 'STATIC_HTML',
                search_url_template VARCHAR(1024),
                list_item_selector VARCHAR(512),
                detail_url_selector VARCHAR(512),
                enabled_detail_fetch BOOLEAN NOT NULL DEFAULT FALSE,
                max_detail_pages INT NOT NULL DEFAULT 10
            )
            """);
        ensureJobSourceColumn("source_type", "VARCHAR(64) DEFAULT 'STATIC_HTML'");
        ensureJobSourceColumn("search_url_template", "VARCHAR(1024)");
        ensureJobSourceColumn("list_item_selector", "VARCHAR(512)");
        ensureJobSourceColumn("detail_url_selector", "VARCHAR(512)");
        ensureJobSourceColumn("enabled_detail_fetch", "BOOLEAN NOT NULL DEFAULT FALSE");
        ensureJobSourceColumn("max_detail_pages", "INT NOT NULL DEFAULT 10");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS job_collect_log (
                id VARCHAR(64) PRIMARY KEY,
                source_id VARCHAR(64),
                source_name VARCHAR(255),
                started_at TIMESTAMP NOT NULL,
                finished_at TIMESTAMP NOT NULL,
                status VARCHAR(64) NOT NULL,
                added INT NOT NULL DEFAULT 0,
                updated INT NOT NULL DEFAULT 0,
                skipped INT NOT NULL DEFAULT 0,
                failed INT NOT NULL DEFAULT 0,
                error_message TEXT,
                searched INT NOT NULL DEFAULT 0,
                detail_fetched INT NOT NULL DEFAULT 0,
                detail_failed INT NOT NULL DEFAULT 0
            )
            """);
        ensureJobCollectLogColumn("searched", "INT NOT NULL DEFAULT 0");
        ensureJobCollectLogColumn("detail_fetched", "INT NOT NULL DEFAULT 0");
        ensureJobCollectLogColumn("detail_failed", "INT NOT NULL DEFAULT 0");
    }

    private void ensureJobSourceColumn(String name, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE job_source ADD COLUMN " + name + " " + definition);
        } catch (RuntimeException ignored) {
            // Column already exists on upgraded databases.
        }
    }

    private void ensureJobCollectLogColumn(String name, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE job_collect_log ADD COLUMN " + name + " " + definition);
        } catch (RuntimeException ignored) {
            // Column already exists on upgraded databases.
        }
    }
}
