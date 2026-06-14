package com.zyagent.job;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryJobCollectorStore implements JobCollectorStore {
    private final Map<String, JobSource> sources = new LinkedHashMap<>();
    private final Map<String, JobPosting> jobsByKey = new LinkedHashMap<>();
    private final List<JobCollectLog> logs = new ArrayList<>();

    @Override
    public List<JobSource> listSources() {
        return List.copyOf(sources.values());
    }

    @Override
    public JobSource saveSource(JobSource source) {
        sources.put(source.id(), source);
        return source;
    }

    @Override
    public void updateSourceStatus(String sourceId, String status) {
        JobSource source = sources.get(sourceId);
        if (source != null) {
            sources.put(sourceId, new JobSource(
                source.id(),
                source.company(),
                source.name(),
                source.url(),
                source.enabled(),
                source.keywords(),
                LocalDateTime.now(),
                status,
                source.sourceType(),
                source.searchUrlTemplate(),
                source.listItemSelector(),
                source.detailUrlSelector(),
                source.enabledDetailFetch(),
                source.maxDetailPages()
            ));
        }
    }

    @Override
    public UpsertResult upsertCollectedJob(JobPosting posting, String sourceName, String externalId, String contentHash) {
        String key = externalId != null && !externalId.isBlank()
            ? externalId
            : posting.sourceUrl() != null && !posting.sourceUrl().isBlank()
                ? posting.sourceUrl()
                : posting.company() + "|" + posting.title() + "|" + posting.city() + "|" + contentHash;
        UpsertResult result = jobsByKey.containsKey(key) ? UpsertResult.UPDATED : UpsertResult.ADDED;
        jobsByKey.put(key, posting);
        return result;
    }

    @Override
    public void saveLog(JobCollectLog log) {
        logs.add(0, log);
    }

    @Override
    public List<JobCollectLog> listLogs() {
        return List.copyOf(logs);
    }

    @Override
    public Optional<JobSource> findSource(String id) {
        return Optional.ofNullable(sources.get(id));
    }

    @Override
    public int deleteInvalidCollectedJobs() {
        int before = jobsByKey.size();
        jobsByKey.entrySet().removeIf(entry -> {
            JobPosting job = entry.getValue();
            return !List.of("mock", "manual", "url").contains(job.sourceName())
                && ("未标注".equals(job.company()) || "未标注".equals(job.title()) || String.valueOf(job.rawText()).toLowerCase().contains("enable javascript to run this app"));
        });
        return before - jobsByKey.size();
    }
}
