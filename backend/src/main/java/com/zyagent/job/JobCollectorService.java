package com.zyagent.job;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class JobCollectorService {
    private final JobCollectorStore store;
    private final JobDescriptionParser parser;
    private final JobCrawler crawler;

    public JobCollectorService(JobCollectorStore store, JobDescriptionParser parser, JobCrawler crawler) {
        this.store = store;
        this.parser = parser;
        this.crawler = crawler;
    }

    public JobCollectResult collectAll(int maxPagesPerSource) {
        JobCollectResult total = JobCollectResult.empty();
        for (JobSource source : store.listSources()) {
            if (source.enabled()) {
                total = total.plus(collect(source, maxPagesPerSource));
            }
        }
        return total;
    }

    public JobCollectResult collect(String sourceId, int maxPagesPerSource) {
        JobSource source = store.findSource(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("采集源不存在：" + sourceId));
        return collect(source, maxPagesPerSource);
    }

    public JobCollectResult collect(JobSource source, int maxPagesPerSource) {
        LocalDateTime started = LocalDateTime.now();
        int detailFailed = 0;
        try {
            List<JobCrawlItem> items = crawler.crawlItems(source, maxPagesPerSource);
            return collectItems(source, items, detailFailed, started);
        } catch (RuntimeException ex) {
            store.updateSourceStatus(source.id(), "FAILED");
            store.saveLog(new JobCollectLog(UUID.randomUUID().toString(), source.id(), source.name(), started, LocalDateTime.now(), "FAILED", 0, 0, 0, 1, ex.getMessage(), 0, 0, detailFailed));
            return new JobCollectResult(0, 0, 0, 1);
        }
    }

    public JobCollectResult collectItems(JobSource source, List<JobCrawlItem> items, int detailFailed) {
        return collectItems(source, items, detailFailed, LocalDateTime.now());
    }

    private JobCollectResult collectItems(JobSource source, List<JobCrawlItem> items, int detailFailed, LocalDateTime started) {
        int added = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int searched = 0;
        int detailFetched = 0;
        String error = null;
        try {
            searched = items.size();
            detailFetched = (int) items.stream().filter(JobCrawlItem::detailFetched).count();
            if (items.isEmpty()) {
                skipped++;
            }
            for (JobCrawlItem item : items) {
                String text = item.text();
                if (text.isBlank()) {
                    skipped++;
                    continue;
                }
                JobPosting posting = parser.parse(text, item.url());
                if (!isValidPosting(posting)) {
                    skipped++;
                    continue;
                }
                String hash = sha256(source.company() + "|" + posting.title() + "|" + posting.city() + "|" + text);
                JobCollectorStore.UpsertResult result = store.upsertCollectedJob(posting, source.name(), stableExternalId(posting, hash), hash);
                if (result == JobCollectorStore.UpsertResult.ADDED) {
                    added++;
                } else if (result == JobCollectorStore.UpsertResult.UPDATED) {
                    updated++;
                } else {
                    skipped++;
                }
            }
            store.updateSourceStatus(source.id(), skipped > 0 && added == 0 && updated == 0 ? "SKIPPED" : "SUCCESS");
        } catch (RuntimeException ex) {
            failed++;
            error = ex.getMessage();
            store.updateSourceStatus(source.id(), "FAILED");
        }
        JobCollectResult result = new JobCollectResult(added, updated, skipped, failed);
        String status = failed > 0 ? "FAILED" : skipped > 0 && added == 0 && updated == 0 ? "SKIPPED" : "SUCCESS";
        String message = error == null && skipped > 0 && added == 0 && updated == 0 ? "未发现可解析的有效岗位，可能是强 JS 渲染页面或岗位字段不完整。" : error;
        store.saveLog(new JobCollectLog(UUID.randomUUID().toString(), source.id(), source.name(), started, LocalDateTime.now(), status, added, updated, skipped, failed, message, searched, detailFetched, detailFailed));
        return result;
    }

    public List<JobSource> listSources() {
        return store.listSources();
    }

    public JobSource saveSource(JobSource source) {
        return store.saveSource(source);
    }

    public List<JobCollectLog> listLogs() {
        return store.listLogs();
    }

    public int deleteInvalidCollectedJobs() {
        return store.deleteInvalidCollectedJobs();
    }

    private String stableExternalId(JobPosting posting, String hash) {
        if (posting.sourceUrl() != null && !posting.sourceUrl().isBlank()) {
            return posting.sourceUrl() + "#" + hash.substring(0, 12);
        }
        return posting.company() + "|" + posting.title() + "|" + posting.city() + "|" + hash.substring(0, 12);
    }

    private boolean isValidPosting(JobPosting posting) {
        return posting != null
            && !isUnknown(posting.company())
            && !isUnknown(posting.title())
            && posting.sourceUrl() != null
            && !posting.sourceUrl().isBlank()
            && !JobCrawler.isInvalidShellText(posting.rawText());
    }

    private boolean isUnknown(String value) {
        return value == null || value.isBlank() || "未标注".equals(value);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
